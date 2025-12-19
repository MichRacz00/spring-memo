package com.example.ui;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import com.example.memo.Utilities;
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
        BlobClient blobClient = blobContainerClient.getBlobClient(blobName);

        if (!blobClient.exists()) {
            return ResponseEntity.noContent().build();
        }

        BlobSasPermission permission = new BlobSasPermission().setReadPermission(true);
        OffsetDateTime expiryTime = OffsetDateTime.now().plusHours(1);

        BlobServiceSasSignatureValues values = new BlobServiceSasSignatureValues(expiryTime, permission)
                .setStartTime(OffsetDateTime.now().minusMinutes(5));

        String sasToken = blobClient.generateSas(values);
        return ResponseEntity.ok(blobClient.getBlobUrl() + "?" + sasToken);
    }

    private String getUserId(OidcUser principal) {
        return Utilities.filterClaims(principal).get("oid");
    }
}
