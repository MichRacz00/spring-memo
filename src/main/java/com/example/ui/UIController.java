package com.example.ui;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.UserDelegationKey;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import com.example.Utilities;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.OffsetDateTime;

@RestController
@RequestMapping("/ui")
public class UIController {

    private final BlobContainerClient blobContainerClient;

    public UIController(BlobServiceClient blobServiceClient) {
        this.blobContainerClient = blobServiceClient.getBlobContainerClient("backgrounds");
        if (!this.blobContainerClient.exists()) {
            this.blobContainerClient.create();
        }
    }

    @PostMapping("/background")
    public ResponseEntity<String> changeBackground(@RequestParam("image") MultipartFile file,
                                                   @AuthenticationPrincipal OidcUser principal) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("File is empty");
        }

        try {
            String blobName = getUserId(principal);

            BlobClient blobClient = blobContainerClient.getBlobClient(blobName);
            blobClient.upload(file.getInputStream(), file.getSize(), true);
            return ResponseEntity.ok(blobClient.getBlobUrl());

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to upload image: " + e.getMessage());
        }
    }

    @GetMapping("/background")
    public ResponseEntity<String> getBackground(@AuthenticationPrincipal OidcUser principal) {
        String blobName = principal.getAttribute("oid");
        assert blobName != null;
        BlobClient blobClient = blobContainerClient.getBlobClient(blobName);

        if (!blobClient.exists()) {
            return ResponseEntity.noContent().build();
        }

        BlobServiceClient serviceClient = blobContainerClient.getServiceClient();

        OffsetDateTime keyStart = OffsetDateTime.now().minusMinutes(5);
        OffsetDateTime keyExpiry = OffsetDateTime.now().plusHours(1);
        UserDelegationKey userDelegationKey = serviceClient.getUserDelegationKey(keyStart, keyExpiry);

        BlobSasPermission permission = new BlobSasPermission().setReadPermission(true);

        BlobServiceSasSignatureValues values = new BlobServiceSasSignatureValues(keyExpiry, permission)
                .setStartTime(keyStart);

        String sasToken = blobClient.generateUserDelegationSas(values, userDelegationKey);
        return ResponseEntity.ok(blobClient.getBlobUrl() + "?" + sasToken);
    }

    private String getUserId(OidcUser principal) {
        return Utilities.filterClaims(principal).get("oid");
    }
}
