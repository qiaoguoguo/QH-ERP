package com.qherp.api.system.platform;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformDeliveryAssetServiceTests {

	@Test
	void deliveryReleaseMetadataUsesHonestDefaultsWhenConfigurationIsMissing() {
		PlatformDeliveryAssetService service = new PlatformDeliveryAssetService(null, "", "", "", "", "", "");

		PlatformDeliveryAssetService.DeliveryReleaseMetadata metadata = service.releaseMetadata();

		assertThat(metadata.environmentCode()).isEqualTo("UNCONFIGURED");
		assertThat(metadata.manual().version()).isEqualTo("UNCONFIGURED");
		assertThat(metadata.manual().updatedAt()).isNull();
		assertThat(metadata.demoData().version()).isEqualTo("UNCONFIGURED");
		assertThat(metadata.demoData().status()).isEqualTo("NOT_VERIFIED");
		assertThat(metadata.demoData().verifiedAt()).isNull();
	}

	@Test
	void deliveryReleaseMetadataUsesConfiguredValuesWithoutChangingTimestamps() {
		PlatformDeliveryAssetService service = new PlatformDeliveryAssetService(null, "stage034",
				"manual-034", "2026-07-22T10:00:00+08:00", "demo-034", "VERIFIED",
				"2026-07-22T11:00:00+08:00");

		PlatformDeliveryAssetService.DeliveryReleaseMetadata metadata = service.releaseMetadata();

		assertThat(metadata.environmentCode()).isEqualTo("stage034");
		assertThat(metadata.manual().version()).isEqualTo("manual-034");
		assertThat(metadata.manual().updatedAt().toString()).isEqualTo("2026-07-22T10:00+08:00");
		assertThat(metadata.demoData().version()).isEqualTo("demo-034");
		assertThat(metadata.demoData().status()).isEqualTo("VERIFIED");
		assertThat(metadata.demoData().verifiedAt().toString()).isEqualTo("2026-07-22T11:00+08:00");
	}

}
