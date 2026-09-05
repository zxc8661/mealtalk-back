package com.mealtalk.api.domain.meal.photo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

/**
 * Chooses the storage implementation from the environment alone.
 *
 * <p>The bucket does not exist yet, so a missing {@code R2_*} variable must not
 * stop the application from starting. Selecting the bean at runtime from
 * {@link MealPhotoStorageProperties#isConfigured()} - rather than with a
 * conditional on individual properties - keeps that decision in one readable
 * place and guarantees exactly one {@link MealPhotoStorage} bean either way.
 * Filling in {@code .env} and restarting is the only step required to go live.
 */
@Configuration
public class MealPhotoStorageConfig {
    private static final Logger log = LoggerFactory.getLogger(MealPhotoStorageConfig.class);

    @Bean
    public MealPhotoStorage mealPhotoStorage(MealPhotoStorageProperties properties) {
        if (!properties.isConfigured()) {
            log.info("R2 사진 저장소 설정이 없어 비활성 저장소로 기동합니다. 사진 업로드는 503으로 거부됩니다.");
            return new UnconfiguredMealPhotoStorage();
        }
        log.info("R2 사진 저장소를 활성화합니다. bucket={}", properties.bucket());
        return new R2MealPhotoStorage(s3Client(properties), properties.bucket());
    }

    private static S3Client s3Client(MealPhotoStorageProperties properties) {
        return S3Client.builder()
            .endpointOverride(URI.create(properties.endpoint()))
            .region(Region.of(properties.region()))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKeyId(), properties.secretAccessKey())
            ))
            // R2 serves buckets from the account endpoint path, not a virtual host.
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .build();
    }
}
