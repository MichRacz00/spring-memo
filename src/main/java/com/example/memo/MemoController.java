package com.example.memo;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.*;
import com.azure.cosmos.util.CosmosPagedIterable;
import org.springframework.http.ResponseEntity;
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
    public List<Memo> getAll() {
        try {
            String sql = "SELECT * FROM c";
            CosmosPagedIterable<Memo> items = cosmosContainer.queryItems(sql,
                    new CosmosQueryRequestOptions(), Memo.class);

            List<Memo> memos = new ArrayList<>();
            items.forEach(memos::add);
            return memos;

        } catch (Exception e) {
            System.err.println("Error retrieving all memos: " + e.getMessage());
            throw new RuntimeException("Failed to retrieve all memos", e);
        }
    }

    @PostMapping("/")
    public Memo addNew(@RequestBody Memo newMemo) {
        UUID id = UUID.randomUUID();
        newMemo.setId(id.toString());

        cosmosContainer.createItem(
                newMemo,
                new PartitionKey(newMemo.getId()),
                new CosmosItemRequestOptions()
        );

        return newMemo;
    }

    @PatchMapping("/coordinates")
    public ResponseEntity<Object> updateCoordinates(@RequestBody Memo updatedMemo) {
        String id = updatedMemo.getId();

        try {
            try {
                cosmosContainer.readItem(id, new PartitionKey(id), Memo.class);
            } catch (CosmosException e) {
                if (e.getStatusCode() == 404) {
                    return ResponseEntity.notFound().build();
                }
                throw e;
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
    public void delete(@PathVariable String id) {
        try {
            cosmosContainer.deleteItem(id, new PartitionKey(id), new CosmosItemRequestOptions());
        } catch (com.azure.cosmos.CosmosException e) {
            if (e.getStatusCode() != 404) throw e;
        }
    }
}
