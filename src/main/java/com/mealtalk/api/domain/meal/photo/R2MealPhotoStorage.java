package com.mealtalk.api.domain.meal.photo;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.Optional;

/**
 * {@link MealPhotoStorage} backed by Cloudflare R2 through its S3-compatible API.
 *
 * <p>The bucket is private: nothing here ever produces a public or signed URL,
 * because clients read photos through the authenticated API instead. Every SDK
 * failure is translated into {@link MealPhotoStorageException} so no vendor type
 * escapes this class.
 */
public class R2MealPhotoStorage implements MealPhotoStorage {
    private final S3Client client;
    private final String bucket;

    public R2MealPhotoStorage(S3Client client, String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    @Override
    public void put(String objectKey, byte[] bytes, String contentType) {
        try {
            client.putObject(
                PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .contentType(contentType)
                    .contentLength((long) bytes.length)
                    .build(),
                RequestBody.fromBytes(bytes)
            );
        } catch (SdkException exception) {
            throw new MealPhotoStorageException("Writing the meal photo object failed", exception);
        }
    }

    @Override
    public Optional<StoredObject> read(String objectKey) {
        try {
            ResponseBytes<GetObjectResponse> response = client.getObjectAsBytes(
                GetObjectRequest.builder().bucket(bucket).key(objectKey).build()
            );
            byte[] bytes = response.asByteArray();
            String contentType = response.response().contentType();
            return Optional.of(new StoredObject(
                objectKey,
                contentType == null ? MealPhotoSanitizer.OUTPUT_CONTENT_TYPE : contentType,
                bytes.length,
                bytes
            ));
        } catch (NoSuchKeyException exception) {
            return Optional.empty();
        } catch (SdkException exception) {
            throw new MealPhotoStorageException("Reading the meal photo object failed", exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(objectKey).build());
        } catch (SdkException exception) {
            throw new MealPhotoStorageException("Deleting the meal photo object failed", exception);
        }
    }
}
