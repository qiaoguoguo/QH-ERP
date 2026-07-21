package com.qherp.api.system.platform;

import com.qherp.api.common.ApiResponse;
import com.qherp.api.security.CurrentUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/platform")
public class PlatformDeliveryAssetController {

	private final PlatformDeliveryAssetService deliveryAssetService;

	public PlatformDeliveryAssetController(PlatformDeliveryAssetService deliveryAssetService) {
		this.deliveryAssetService = deliveryAssetService;
	}

	@GetMapping("/delivery-assets")
	public ApiResponse<PlatformDeliveryAssetService.DeliveryAssetCatalog> catalog(
			@AuthenticationPrincipal CurrentUser currentUser) {
		return ApiResponse.ok(this.deliveryAssetService.catalog(currentUser));
	}

}
