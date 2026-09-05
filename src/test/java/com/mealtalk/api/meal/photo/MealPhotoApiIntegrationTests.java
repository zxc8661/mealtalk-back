package com.mealtalk.api.meal.photo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mealtalk.api.domain.auth.google.GoogleTokenPayload;
import com.mealtalk.api.domain.auth.google.GoogleTokenVerifier;
import com.mealtalk.api.domain.food.repository.FoodRepository;
import com.mealtalk.api.domain.meal.photo.MealPhotoStorage;
import com.mealtalk.api.domain.meal.repository.MealItemRepository;
import com.mealtalk.api.domain.meal.repository.MealPhotoRepository;
import com.mealtalk.api.domain.meal.repository.MealRepository;
import com.mealtalk.api.domain.meal.repository.MealWriteRequestRepository;
import com.mealtalk.api.domain.user.repository.UserProfileRepository;
import com.mealtalk.api.domain.user.repository.UserRepository;
import com.mealtalk.api.domain.user.repository.UserTargetRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The multipart meal-photo lifecycle: create/update with a required {@code meal}
 * JSON part and an optional {@code photo} binary part, owner-scoped authenticated
 * streaming, idempotent create, and the error mapping the shared envelope owes
 * each failure.
 *
 * <p>Storage is a deterministic in-memory double so nothing here touches R2 or
 * the network; it also lets a test force a write failure and then assert that no
 * row and no object survived.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(MealPhotoApiIntegrationTests.FakeStorageConfig.class)
class MealPhotoApiIntegrationTests {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private MockMvc mockMvc;
    @Autowired private InMemoryMealPhotoStorage storage;
    @Autowired private MealPhotoRepository mealPhotoRepository;
    @Autowired private MealItemRepository mealItemRepository;
    @Autowired private MealRepository mealRepository;
    @Autowired private MealWriteRequestRepository mealWriteRequestRepository;
    @Autowired private FoodRepository foodRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private UserTargetRepository userTargetRepository;
    @MockitoBean private GoogleTokenVerifier googleTokenVerifier;

    @TestConfiguration
    static class FakeStorageConfig {
        @Bean
        @Primary
        InMemoryMealPhotoStorage inMemoryMealPhotoStorage() {
            return new InMemoryMealPhotoStorage();
        }
    }

    @AfterEach
    void clearData() {
        storage.reset();
        mealWriteRequestRepository.deleteAll();
        mealPhotoRepository.deleteAll();
        mealItemRepository.deleteAll();
        mealRepository.deleteAll();
        foodRepository.deleteAll();
        userTargetRepository.deleteAll();
        userProfileRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ---------------------------------------------------------------- create

    @Test
    @DisplayName("Multipart create with photo and memo returns 201 and a photo block with an authenticated path")
    void createsRecordWithPhotoAndMemo() throws Exception {
        String token = login("photo-create-token", "photo-create", "photo-create@example.com");

        MvcResult result = mockMvc.perform(multipartCreate(token)
                .file(mealPart("{\"mealDate\":\"2026-08-29\",\"mealType\":\"LUNCH\",\"memo\":\"김치찌개\"}"))
                .file(photoPart(MealPhotoFixtures.png(1200, 900))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.memo").value("김치찌개"))
            .andExpect(jsonPath("$.photo.id").isNumber())
            .andExpect(jsonPath("$.photo.width").value(1200))
            .andExpect(jsonPath("$.photo.height").value(900))
            .andExpect(jsonPath("$.photo.contentType").value("image/jpeg"))
            .andExpect(jsonPath("$.photo.byteSize").isNumber())
            .andExpect(jsonPath("$.photo.checksumSha256").isString())
            .andReturn();

        JsonNode body = JSON.readTree(result.getResponse().getContentAsString());
        long mealId = body.required("id").longValue();
        long photoId = body.required("photo").required("id").longValue();
        assertThat(body.required("photo").required("url").asText())
            .as("the client must be handed the authenticated content path, not a storage URL")
            .isEqualTo("/api/v1/meals/" + mealId + "/photo?revision=" + photoId);

        assertThat(storage.keys()).as("exactly one sanitized object must reach the bucket").hasSize(1);
        assertNoStorageLeak(result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("Photo-only create with no memo is accepted: the photo carries the record")
    void createsPhotoOnlyRecord() throws Exception {
        String token = login("photo-only-token", "photo-only", "photo-only@example.com");

        mockMvc.perform(multipartCreate(token)
                .file(mealPart("{\"mealDate\":\"2026-08-29\",\"mealType\":\"DINNER\"}"))
                .file(photoPart(MealPhotoFixtures.png(640, 480))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.memo").doesNotExist())
            .andExpect(jsonPath("$.photo.id").isNumber());
    }

    @Test
    @DisplayName("Memo-only create with no photo part is accepted and reports no photo block")
    void createsMemoOnlyRecord() throws Exception {
        String token = login("memo-only-token", "memo-only", "memo-only@example.com");

        mockMvc.perform(multipartCreate(token)
                .file(mealPart("{\"mealDate\":\"2026-08-29\",\"mealType\":\"BREAKFAST\",\"memo\":\"토스트\"}")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.memo").value("토스트"))
            .andExpect(jsonPath("$.photo").doesNotExist());

        assertThat(storage.keys()).as("a memo-only record must not write any object").isEmpty();
    }

    @Test
    @DisplayName("Neither memo nor photo is a 400 through the shared validation envelope")
    void rejectsEmptyRecord() throws Exception {
        String token = login("empty-multipart-token", "empty-multipart", "empty-multipart@example.com");

        mockMvc.perform(multipartCreate(token)
                .file(mealPart("{\"mealDate\":\"2026-08-29\",\"mealType\":\"LUNCH\",\"memo\":\"   \"}")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertThat(mealRepository.count()).as("a rejected create must persist nothing").isZero();
    }

    @Test
    @DisplayName("A missing meal part is a 400, not a 500")
    void rejectsMissingMealPart() throws Exception {
        String token = login("missing-part-token", "missing-part", "missing-part@example.com");

        mockMvc.perform(multipartCreate(token)
                .file(photoPart(MealPhotoFixtures.png(200, 200))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("A meal part that is not valid JSON is a 400 malformed request")
    void rejectsUnparseableMealPart() throws Exception {
        String token = login("bad-json-token", "bad-json", "bad-json@example.com");

        mockMvc.perform(multipartCreate(token)
                .file(mealPart("{ this is not json }")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    // ------------------------------------------------------------- streaming

    @Test
    @DisplayName("The owner streams the sanitized JPEG with private cache headers")
    void ownerStreamsSanitizedJpeg() throws Exception {
        String token = login("stream-token", "stream-owner", "stream@example.com");
        JsonNode created = createWithPhoto(token, "2026-08-29", "LUNCH", "스트리밍", MealPhotoFixtures.png(800, 600));
        long mealId = created.required("id").longValue();
        long photoId = created.required("photo").required("id").longValue();

        MvcResult result = mockMvc.perform(get("/api/v1/meals/{mealId}/photo", mealId)
                .param("revision", String.valueOf(photoId))
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "image/jpeg"))
            .andExpect(header().string("Cache-Control", "no-store, private"))
            .andReturn();

        byte[] bytes = result.getResponse().getContentAsByteArray();
        assertThat(bytes.length).as("the response must carry the stored bytes").isGreaterThan(100);
        assertThat(new byte[] {bytes[0], bytes[1], bytes[2]})
            .as("a real JPEG starts FF D8 FF")
            .isEqualTo(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
    }

    @Test
    @DisplayName("Absent, stale, foreign-owner and deleted photo reads are byte-identical 404s")
    void photoNotFoundIsIndistinguishable() throws Exception {
        String ownerToken = login("indist-owner-token", "indist-owner", "indist-owner@example.com");
        String otherToken = login("indist-other-token", "indist-other", "indist-other@example.com");

        JsonNode withPhoto = createWithPhoto(ownerToken, "2026-08-29", "LUNCH", "사진", MealPhotoFixtures.png(400, 300));
        long photoMealId = withPhoto.required("id").longValue();
        long photoId = withPhoto.required("photo").required("id").longValue();

        JsonNode memoOnly = createMemoOnly(ownerToken, "2026-08-29", "DINNER", "메모만");
        long memoOnlyMealId = memoOnly.required("id").longValue();

        String absent = photoResponseBody(ownerToken, memoOnlyMealId, photoId);
        String stale = photoResponseBody(ownerToken, photoMealId, photoId + 999);
        String foreign = photoResponseBody(otherToken, photoMealId, photoId);

        mockMvc.perform(delete("/api/v1/meals/{mealId}", photoMealId)
                .header("Authorization", "Bearer " + ownerToken))
            .andExpect(status().isNoContent());
        String deleted = photoResponseBody(ownerToken, photoMealId, photoId);

        assertThat(stale).as("a stale revision must not be distinguishable from an absent photo").isEqualTo(absent);
        assertThat(foreign).as("a foreign owner must not learn the photo exists").isEqualTo(absent);
        assertThat(deleted).as("a deleted meal must not be distinguishable from an absent photo").isEqualTo(absent);
    }

    @Test
    @DisplayName("An anonymous photo read is 401, never the bytes")
    void anonymousPhotoReadIsUnauthorized() throws Exception {
        String token = login("anon-photo-token", "anon-photo", "anon-photo@example.com");
        JsonNode created = createWithPhoto(token, "2026-08-29", "LUNCH", "비공개", MealPhotoFixtures.png(320, 240));

        mockMvc.perform(get("/api/v1/meals/{mealId}/photo", created.required("id").longValue())
                .param("revision", created.required("photo").required("id").asText()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    // ---------------------------------------------------------------- update

    @Test
    @DisplayName("KEEP with no file leaves the existing photo and its revision untouched")
    void keepPreservesPhoto() throws Exception {
        String token = login("keep-token", "keep-owner", "keep@example.com");
        JsonNode created = createWithPhoto(token, "2026-08-29", "LUNCH", "원본", MealPhotoFixtures.png(500, 400));
        long mealId = created.required("id").longValue();
        long photoId = created.required("photo").required("id").longValue();

        mockMvc.perform(multipartUpdate(token, mealId)
                .file(mealPart("{\"mealDate\":\"2026-08-30\",\"mealType\":\"DINNER\",\"memo\":\"수정\",\"photoAction\":\"KEEP\"}")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.memo").value("수정"))
            .andExpect(jsonPath("$.photo.id").value(photoId));

        assertThat(storage.keys()).as("KEEP must not write or remove any object").hasSize(1);
    }

    @Test
    @DisplayName("REPLACE with a file swaps the bytes, issues a new revision and removes the superseded object")
    void replaceSwapsPhotoAndCleansUpOldObject() throws Exception {
        String token = login("replace-token", "replace-owner", "replace@example.com");
        JsonNode created = createWithPhoto(token, "2026-08-29", "LUNCH", "원본", MealPhotoFixtures.png(500, 400));
        long mealId = created.required("id").longValue();
        long oldPhotoId = created.required("photo").required("id").longValue();
        String oldKey = storage.keys().iterator().next();

        MvcResult result = mockMvc.perform(multipartUpdate(token, mealId)
                .file(mealPart("{\"mealDate\":\"2026-08-29\",\"mealType\":\"LUNCH\",\"memo\":\"교체\",\"photoAction\":\"REPLACE\"}"))
                .file(photoPart(MealPhotoFixtures.png(900, 700))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.photo.width").value(900))
            .andExpect(jsonPath("$.photo.height").value(700))
            .andReturn();

        JsonNode body = JSON.readTree(result.getResponse().getContentAsString());
        assertThat(storage.keys())
            .as("the superseded object must be cleaned up after the commit")
            .hasSize(1)
            .doesNotContain(oldKey);

        long newPhotoId = body.required("photo").required("id").longValue();
        mockMvc.perform(get("/api/v1/meals/{mealId}/photo", mealId)
                .param("revision", String.valueOf(oldPhotoId))
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/meals/{mealId}/photo", mealId)
                .param("revision", String.valueOf(newPhotoId))
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("REMOVE drops the photo, keeps the memo record and deletes the object")
    void removeDropsPhotoAndObject() throws Exception {
        String token = login("remove-token", "remove-owner", "remove@example.com");
        JsonNode created = createWithPhoto(token, "2026-08-29", "LUNCH", "메모 있음", MealPhotoFixtures.png(500, 400));
        long mealId = created.required("id").longValue();

        mockMvc.perform(multipartUpdate(token, mealId)
                .file(mealPart("{\"mealDate\":\"2026-08-29\",\"mealType\":\"LUNCH\",\"memo\":\"메모 있음\",\"photoAction\":\"REMOVE\"}")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.photo").doesNotExist());

        assertThat(storage.keys()).as("REMOVE must delete the object after the commit").isEmpty();
        assertThat(mealPhotoRepository.findByMealId(mealId)).isEmpty();
    }

    @Test
    @DisplayName("REMOVE on a memo-less record is a 400: the record would be left empty")
    void removeOnPhotoOnlyRecordIsRejected() throws Exception {
        String token = login("remove-empty-token", "remove-empty", "remove-empty@example.com");
        JsonNode created = createWithPhoto(token, "2026-08-29", "LUNCH", null, MealPhotoFixtures.png(500, 400));
        long mealId = created.required("id").longValue();

        mockMvc.perform(multipartUpdate(token, mealId)
                .file(mealPart("{\"mealDate\":\"2026-08-29\",\"mealType\":\"LUNCH\",\"photoAction\":\"REMOVE\"}")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertThat(storage.keys()).as("a rejected REMOVE must leave the object in place").hasSize(1);
        assertThat(mealPhotoRepository.findByMealId(mealId)).as("the photo row must survive").isPresent();
    }

    @Test
    @DisplayName("Inconsistent action/part combinations are all 400")
    void rejectsInconsistentPhotoActions() throws Exception {
        String token = login("action-token", "action-owner", "action@example.com");
        JsonNode created = createWithPhoto(token, "2026-08-29", "LUNCH", "액션", MealPhotoFixtures.png(400, 300));
        long mealId = created.required("id").longValue();

        // KEEP + file
        mockMvc.perform(multipartUpdate(token, mealId)
                .file(mealPart("{\"mealDate\":\"2026-08-29\",\"mealType\":\"LUNCH\",\"memo\":\"액션\",\"photoAction\":\"KEEP\"}"))
                .file(photoPart(MealPhotoFixtures.png(100, 100))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        // REPLACE without file
        mockMvc.perform(multipartUpdate(token, mealId)
                .file(mealPart("{\"mealDate\":\"2026-08-29\",\"mealType\":\"LUNCH\",\"memo\":\"액션\",\"photoAction\":\"REPLACE\"}")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        // REMOVE + file
        mockMvc.perform(multipartUpdate(token, mealId)
                .file(mealPart("{\"mealDate\":\"2026-08-29\",\"mealType\":\"LUNCH\",\"memo\":\"액션\",\"photoAction\":\"REMOVE\"}"))
                .file(photoPart(MealPhotoFixtures.png(100, 100))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        // Missing action entirely
        mockMvc.perform(multipartUpdate(token, mealId)
                .file(mealPart("{\"mealDate\":\"2026-08-29\",\"mealType\":\"LUNCH\",\"memo\":\"액션\"}")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertThat(storage.putCount())
            .as("no rejected update may write an object")
            .isEqualTo(1);
    }

    @Test
    @DisplayName("A foreign owner cannot update another user's record")
    void foreignUpdateIsNotFound() throws Exception {
        String ownerToken = login("upd-owner-token", "upd-owner", "upd-owner@example.com");
        String otherToken = login("upd-other-token", "upd-other", "upd-other@example.com");
        long mealId = createWithPhoto(ownerToken, "2026-08-29", "LUNCH", "내 기록", MealPhotoFixtures.png(300, 200))
            .required("id").longValue();

        mockMvc.perform(multipartUpdate(otherToken, mealId)
                .file(mealPart("{\"mealDate\":\"2026-08-29\",\"mealType\":\"LUNCH\",\"memo\":\"침입\",\"photoAction\":\"REMOVE\"}")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        assertThat(mealPhotoRepository.findByMealId(mealId)).isPresent();
    }

    // ----------------------------------------------------------- list & read

    @Test
    @DisplayName("The list response carries the same photo block and never a storage key")
    void listCarriesPhotoBlock() throws Exception {
        String token = login("list-photo-token", "list-photo", "list-photo@example.com");
        JsonNode created = createWithPhoto(token, "2026-08-29", "LUNCH", "목록", MealPhotoFixtures.png(600, 400));
        long mealId = created.required("id").longValue();
        long photoId = created.required("photo").required("id").longValue();

        MvcResult result = mockMvc.perform(get("/api/v1/meals?date=2026-08-29")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.meals[0].photo.id").value(photoId))
            .andExpect(jsonPath("$.meals[0].photo.url")
                .value("/api/v1/meals/" + mealId + "/photo?revision=" + photoId))
            .andReturn();

        assertNoStorageLeak(result.getResponse().getContentAsString());
    }

    // ------------------------------------------------------------ idempotency

    @Test
    @DisplayName("A repeated create with the same client request id returns the same record")
    void repeatedCreateIsIdempotent() throws Exception {
        String token = login("idem-token", "idem-owner", "idem@example.com");
        String requestId = UUID.randomUUID().toString();
        String mealJson = "{\"mealDate\":\"2026-08-29\",\"mealType\":\"LUNCH\",\"memo\":\"재시도\","
            + "\"clientRequestId\":\"" + requestId + "\"}";

        MvcResult first = mockMvc.perform(multipartCreate(token)
                .file(mealPart(mealJson))
                .file(photoPart(MealPhotoFixtures.png(400, 300))))
            .andExpect(status().isCreated())
            .andReturn();
        long firstId = JSON.readTree(first.getResponse().getContentAsString()).required("id").longValue();

        MvcResult second = mockMvc.perform(multipartCreate(token)
                .file(mealPart(mealJson))
                .file(photoPart(MealPhotoFixtures.png(400, 300))))
            .andExpect(status().isCreated())
            .andReturn();
        long secondId = JSON.readTree(second.getResponse().getContentAsString()).required("id").longValue();

        assertThat(secondId).as("the retry must return the original record, not a new one").isEqualTo(firstId);
        assertThat(mealRepository.count()).as("no duplicate record may be created").isEqualTo(1);
        assertThat(storage.keys()).as("the retry must not leave a second object behind").hasSize(1);
    }

    @Test
    @DisplayName("Reusing a client request id for a different payload is a 409")
    void conflictingRequestIdReuseIsConflict() throws Exception {
        String token = login("idem-conflict-token", "idem-conflict", "idem-conflict@example.com");
        String requestId = UUID.randomUUID().toString();

        mockMvc.perform(multipartCreate(token)
                .file(mealPart("{\"mealDate\":\"2026-08-29\",\"mealType\":\"LUNCH\",\"memo\":\"처음\","
                    + "\"clientRequestId\":\"" + requestId + "\"}")))
            .andExpect(status().isCreated());

        mockMvc.perform(multipartCreate(token)
                .file(mealPart("{\"mealDate\":\"2026-08-30\",\"mealType\":\"DINNER\",\"memo\":\"다른 내용\","
                    + "\"clientRequestId\":\"" + requestId + "\"}")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONFLICT"));

        assertThat(mealRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Two users may use the same client request id independently")
    void requestIdIsScopedPerUser() throws Exception {
        String first = login("idem-user-a-token", "idem-user-a", "idem-a@example.com");
        String second = login("idem-user-b-token", "idem-user-b", "idem-b@example.com");
        String requestId = UUID.randomUUID().toString();
        String mealJson = "{\"mealDate\":\"2026-08-29\",\"mealType\":\"LUNCH\",\"memo\":\"공유 아이디\","
            + "\"clientRequestId\":\"" + requestId + "\"}";

        mockMvc.perform(multipartCreate(first).file(mealPart(mealJson)))
            .andExpect(status().isCreated());
        mockMvc.perform(multipartCreate(second).file(mealPart(mealJson)))
            .andExpect(status().isCreated());

        assertThat(mealRepository.count()).as("one record per user, no cross-user collision").isEqualTo(2);
    }

    @Test
    @DisplayName("A client request id that is not a UUID is a 400")
    void malformedRequestIdIsRejected() throws Exception {
        String token = login("idem-bad-token", "idem-bad", "idem-bad@example.com");

        mockMvc.perform(multipartCreate(token)
                .file(mealPart("{\"mealDate\":\"2026-08-29\",\"mealType\":\"LUNCH\",\"memo\":\"메모\","
                    + "\"clientRequestId\":\"not-a-uuid\"}")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // --------------------------------------------------------- error mapping

    @Test
    @DisplayName("A corrupt photo is 400, an SVG is 415, an oversized photo is 413")
    void mapsPhotoValidationFailuresToTheirStatuses() throws Exception {
        String token = login("photo-error-token", "photo-error", "photo-error@example.com");
        String mealJson = "{\"mealDate\":\"2026-08-29\",\"mealType\":\"LUNCH\",\"memo\":\"오류\"}";

        mockMvc.perform(multipartCreate(token)
                .file(mealPart(mealJson))
                .file(photoPart(MealPhotoFixtures.corrupt())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(multipartCreate(token)
                .file(mealPart(mealJson))
                .file(photoPart(new byte[0])))
            .andExpect(status().isBadRequest());

        mockMvc.perform(multipartCreate(token)
                .file(mealPart(mealJson))
                .file(photoPart(MealPhotoFixtures.svg())))
            .andExpect(status().isBadRequest());

        mockMvc.perform(multipartCreate(token)
                .file(mealPart(mealJson))
                .file(photoPart(MealPhotoFixtures.animatedGif(48, 48))))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));

        mockMvc.perform(multipartCreate(token)
                .file(mealPart(mealJson))
                .file(photoPart(MealPhotoFixtures.decompressionBombPng(8000, 6000))))
            .andExpect(status().isPayloadTooLarge())
            .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));

        assertThat(mealRepository.count()).as("no rejected upload may create a record").isZero();
        assertThat(storage.keys()).as("no rejected upload may write an object").isEmpty();
    }

    @Test
    @DisplayName("A storage outage is 503 and leaves no record and no object")
    void storageOutageIsServiceUnavailableWithNoMutation() throws Exception {
        String token = login("outage-token", "outage-owner", "outage@example.com");
        storage.failNextPut();

        mockMvc.perform(multipartCreate(token)
                .file(mealPart("{\"mealDate\":\"2026-08-29\",\"mealType\":\"LUNCH\",\"memo\":\"저장소 장애\"}"))
                .file(photoPart(MealPhotoFixtures.png(400, 300))))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("STORAGE_UNAVAILABLE"));

        assertThat(mealRepository.count()).as("a storage failure must leave no row").isZero();
        assertThat(storage.keys()).as("a storage failure must leave no object").isEmpty();

        mockMvc.perform(get("/api/v1/meals?date=2026-08-29")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.meals.length()").value(0));
    }

    @Test
    @DisplayName("An owner-check failure on update never uploads, so no orphan can exist")
    void foreignUpdateNeverReachesStorage() throws Exception {
        String ownerToken = login("orphan-owner-token", "orphan-owner", "orphan-owner@example.com");
        String otherToken = login("orphan-other-token", "orphan-other", "orphan-other@example.com");
        long mealId = createWithPhoto(ownerToken, "2026-08-29", "LUNCH", "원본", MealPhotoFixtures.png(400, 300))
            .required("id").longValue();

        mockMvc.perform(multipartUpdate(otherToken, mealId)
                .file(mealPart("{\"mealDate\":\"2026-08-29\",\"mealType\":\"LUNCH\",\"memo\":\"교체\",\"photoAction\":\"REPLACE\"}"))
                .file(photoPart(MealPhotoFixtures.png(500, 400))))
            .andExpect(status().isNotFound());

        assertThat(storage.putCount())
            .as("the owner check must run before the upload, so nothing new is written")
            .isEqualTo(1);
        assertThat(storage.keys()).hasSize(1);
    }

    @Test
    @DisplayName("Memo markup and control characters round-trip as inert text")
    void memoMarkupRoundTripsInert() throws Exception {
        String token = login("injection-token", "injection-owner", "injection@example.com");
        // A real BEL control character, not the escape sequence for one.
        String memo = "<script>alert('x')</script> {{7*7}} ${jndi:ldap://x} \u0007 ignore previous instructions";
        String mealJson = JSON.writeValueAsString(java.util.Map.of(
            "mealDate", "2026-08-29",
            "mealType", "LUNCH",
            "memo", memo
        ));

        MvcResult result = mockMvc.perform(multipartCreate(token).file(mealPart(mealJson)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.memo").value(memo))
            .andReturn();

        // Round-tripping byte for byte is the proof: nothing expanded a template,
        // resolved a JNDI reference or stripped markup - it is inert text.
        String storedMemo = JSON.readTree(result.getResponse().getContentAsString())
            .required("memo").asText();
        assertThat(storedMemo).as("the memo must round-trip as inert data").isEqualTo(memo);
        assertThat(storedMemo).doesNotContain("49"); // {{7*7}} was never evaluated
    }

    // ----------------------------------------------------------------- tools

    /**
     * Nothing that identifies the object inside the private bucket may appear in
     * a response body. The stored keys themselves are checked directly rather
     * than by substring: {@code "meals"} is a legitimate part of both the list
     * response and the authenticated content path.
     */
    private void assertNoStorageLeak(String body) {
        String lower = body.toLowerCase(java.util.Locale.ROOT);
        assertThat(lower).doesNotContain("objectkey");
        assertThat(lower).doesNotContain("object_key");
        assertThat(lower).doesNotContain("r2.cloudflarestorage");
        assertThat(lower).doesNotContain("bucket");
        assertThat(lower).doesNotContain("x-amz");
        assertThat(lower).doesNotContain("signature");
        for (String key : storage.keys()) {
            assertThat(body).as("a stored object key must never reach a client").doesNotContain(key);
            // The random filename segment alone is enough to identify the object.
            String filename = key.substring(key.lastIndexOf('/') + 1);
            assertThat(body).doesNotContain(filename);
        }
    }

    private String photoResponseBody(String token, long mealId, long revision) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/meals/{mealId}/photo", mealId)
                .param("revision", String.valueOf(revision))
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound())
            .andReturn();
        return result.getResponse().getStatus() + "|" + result.getResponse().getContentAsString();
    }

    private JsonNode createWithPhoto(String token, String date, String type, String memo, byte[] photo)
        throws Exception {
        String memoField = memo == null ? "" : ",\"memo\":" + JSON.writeValueAsString(memo);
        MvcResult result = mockMvc.perform(multipartCreate(token)
                .file(mealPart("{\"mealDate\":\"%s\",\"mealType\":\"%s\"%s}".formatted(date, type, memoField)))
                .file(photoPart(photo)))
            .andExpect(status().isCreated())
            .andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode createMemoOnly(String token, String date, String type, String memo) throws Exception {
        MvcResult result = mockMvc.perform(multipartCreate(token)
                .file(mealPart("{\"mealDate\":\"%s\",\"mealType\":\"%s\",\"memo\":%s}"
                    .formatted(date, type, JSON.writeValueAsString(memo)))))
            .andExpect(status().isCreated())
            .andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private static MockMultipartHttpServletRequestBuilder multipartCreate(String token) {
        MockMultipartHttpServletRequestBuilder builder = multipart("/api/v1/meals");
        builder.header("Authorization", "Bearer " + token);
        return builder;
    }

    private static MockMultipartHttpServletRequestBuilder multipartUpdate(String token, long mealId) {
        MockMultipartHttpServletRequestBuilder builder = multipart("/api/v1/meals/{mealId}", mealId);
        builder.with(request -> {
            request.setMethod("PUT");
            return request;
        });
        builder.header("Authorization", "Bearer " + token);
        return builder;
    }

    private static MockMultipartFile mealPart(String json) {
        return new MockMultipartFile(
            "meal", "meal.json", MediaType.APPLICATION_JSON_VALUE, json.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static MockMultipartFile photoPart(byte[] bytes) {
        return new MockMultipartFile("photo", "photo.png", MediaType.IMAGE_PNG_VALUE, bytes);
    }

    private String login(String token, String subject, String email) throws Exception {
        when(googleTokenVerifier.verify(token)).thenReturn(new GoogleTokenPayload(subject, email, "Photo User"));
        MockHttpServletRequestBuilder request = post("/api/v1/auth/google")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"idToken\":\"" + token + "\"}");
        String response = mockMvc.perform(request)
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return response.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
    }
}
