package com.example.memo;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.*;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/memo")
public class MemoController {

    private final CosmosContainer cosmosContainer;

    public MemoController(Database database) {
        CosmosClient client = database.getCosmosClient();
        this.cosmosContainer = client.getDatabase("memo").getContainer("memo-cards");
    }

    @GetMapping("/all")
    public List<Memo> getAll(@AuthenticationPrincipal OidcUser principal) {
        String userId = getUserId(principal);
        try {
            String sql = "SELECT * FROM c WHERE c.userId = @userId";
            SqlQuerySpec querySpec = new SqlQuerySpec(sql,
                    Collections.singletonList(new SqlParameter("@userId", userId)));

            List<Memo> memos = new ArrayList<>();
            cosmosContainer.queryItems(querySpec, new CosmosQueryRequestOptions(), Memo.class)
                    .stream()
                    .forEach(memos::add);

            return memos;

        } catch (Exception e) {
            System.err.println("Error retrieving all memos: " + e.getMessage());
            throw new RuntimeException("Failed to retrieve all memos", e);
        }
    }

    @PostMapping("/")
    public Memo addNew(@RequestBody Memo newMemo, @AuthenticationPrincipal OidcUser principal) {
        String userId = getUserId(principal);
        UUID id = UUID.randomUUID();

        newMemo.setId(id.toString());
        newMemo.setUserId(userId);

        cosmosContainer.createItem(
                newMemo,
                new PartitionKey(newMemo.getId()),
                new CosmosItemRequestOptions()
        );

        return newMemo;
    }

    @PatchMapping("/coordinates")
    public ResponseEntity<Object> updateCoordinates(@RequestBody Memo updatedMemo, @AuthenticationPrincipal OidcUser principal) {
        String userId = getUserId(principal);
        String id = updatedMemo.getId();

        try {
            Memo existingMemo;
            try {
                CosmosItemResponse<Memo> itemResponse = cosmosContainer.readItem(
                        id,
                        new PartitionKey(id),
                        Memo.class
                );
                existingMemo = itemResponse.getItem();

            } catch (CosmosException e) {
                if (e.getStatusCode() == 404) {
                    return ResponseEntity.notFound().build();
                }
                throw e;
            }

            if (!existingMemo.getUserId().equals(userId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            CosmosItemResponse<Memo> response = cosmosContainer.replaceItem(updatedMemo, id,
                    new PartitionKey(id), new CosmosItemRequestOptions());

            return ResponseEntity.ok(response.getItem());

        } catch (CosmosException e) {
            System.err.println("Error updating coordinates for a memo: " + e.getMessage());
            throw new RuntimeException("Failed to overwrite memo", e);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Object> delete(@PathVariable String id, @AuthenticationPrincipal OidcUser principal) {
        String userId = getUserId(principal);

        try {
            CosmosItemResponse<Memo> itemResponse = cosmosContainer.readItem(
                    id,
                    new PartitionKey(id),
                    Memo.class
            );
            Memo existingMemo = itemResponse.getItem();

            if (!existingMemo.getUserId().equals(userId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            } else {
                cosmosContainer.deleteItem(id, new PartitionKey(id), new CosmosItemRequestOptions());
            }
        } catch (com.azure.cosmos.CosmosException e) {
            if (e.getStatusCode() != 404) throw e;
        }
        return ResponseEntity.status(HttpStatus.OK).build();
    }

    private String getUserId(OidcUser principal) {
        return Utilities.filterClaims(principal).get("oid");
    }
}
