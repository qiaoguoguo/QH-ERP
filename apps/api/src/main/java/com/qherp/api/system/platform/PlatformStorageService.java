package com.qherp.api.system.platform;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.net.URI;

@Service
public class PlatformStorageService {

	private final S3Client s3Client;

	private final String bucket;

	private volatile boolean bucketReady;

	public PlatformStorageService(@Value("${qherp.storage.s3.endpoint}") String endpoint,
			@Value("${qherp.storage.s3.region}") String region, @Value("${qherp.storage.s3.bucket}") String bucket,
			@Value("${qherp.storage.s3.access-key}") String accessKey,
			@Value("${qherp.storage.s3.secret-key}") String secretKey,
			@Value("${qherp.storage.s3.path-style:true}") boolean pathStyleAccess) {
		this.bucket = bucket;
		this.s3Client = S3Client.builder()
			.endpointOverride(URI.create(endpoint))
			.region(Region.of(region))
			.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
			.serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(pathStyleAccess).build())
			.build();
	}

	private void ensureBucket() {
		if (this.bucketReady) {
			return;
		}
		try {
			this.s3Client.headBucket(HeadBucketRequest.builder().bucket(this.bucket).build());
			this.bucketReady = true;
		}
		catch (NoSuchBucketException exception) {
			this.s3Client.createBucket(CreateBucketRequest.builder().bucket(this.bucket).build());
			this.bucketReady = true;
		}
		catch (S3Exception exception) {
			if (exception.statusCode() == 404) {
				this.s3Client.createBucket(CreateBucketRequest.builder().bucket(this.bucket).build());
				this.bucketReady = true;
				return;
			}
			throw storageUnavailable(exception);
		}
		catch (RuntimeException exception) {
			throw storageUnavailable(exception);
		}
	}

	public StoredObject put(String objectKey, byte[] content, String contentType) {
		try {
			ensureBucket();
			var response = this.s3Client.putObject(PutObjectRequest.builder()
				.bucket(this.bucket)
				.key(objectKey)
				.contentType(contentType)
				.contentLength((long) content.length)
				.build(), RequestBody.fromBytes(content));
			return new StoredObject(this.bucket, objectKey, response.eTag());
		}
		catch (RuntimeException exception) {
			throw storageUnavailable(exception);
		}
	}

	public byte[] get(String objectKey) {
		try {
			ensureBucket();
			ResponseBytes<GetObjectResponse> bytes = this.s3Client
				.getObjectAsBytes(GetObjectRequest.builder().bucket(this.bucket).key(objectKey).build());
			return bytes.asByteArray();
		}
		catch (RuntimeException exception) {
			throw storageUnavailable(exception);
		}
	}

	public void deleteQuietly(String objectKey) {
		try {
			this.s3Client.deleteObject(DeleteObjectRequest.builder().bucket(this.bucket).key(objectKey).build());
		}
		catch (RuntimeException ignored) {
		}
	}

	public void delete(String objectKey) {
		try {
			ensureBucket();
			this.s3Client.deleteObject(DeleteObjectRequest.builder().bucket(this.bucket).key(objectKey).build());
		}
		catch (RuntimeException exception) {
			throw storageUnavailable(exception);
		}
	}

	public String bucket() {
		return this.bucket;
	}

	private BusinessException storageUnavailable(RuntimeException exception) {
		return new BusinessException(ApiErrorCode.FILE_STORAGE_UNAVAILABLE,
				ApiErrorCode.FILE_STORAGE_UNAVAILABLE.message());
	}

	public record StoredObject(String bucket, String objectKey, String eTag) {
	}

}
