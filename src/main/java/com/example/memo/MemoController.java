package com.example.memo;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.web.bind.annotation.*;

import javax.xml.crypto.Data;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.io.File;
import java.io.IOException;

import static java.lang.Math.max;

@RestController
@RequestMapping("/memo")
public class MemoController {

    private final Database database;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String FILE_PATH = "src/main/resources/memos.json";

    private HashMap<Integer, Memo> memos = new HashMap<>();
    private int nextId = 0;

    public MemoController(Database database) {
        this.database = database;
    }

    @PostConstruct
    public void init() {
        File file = new File(FILE_PATH);

        if (!file.exists() || file.length() == 0) {
            try (FileWriter fw = new FileWriter(file)) {
                fw.write("[]"); // initialize JSON array
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {
            List<Memo> allMemos = mapper.readValue(file, new TypeReference<>() {});
            nextId++;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @GetMapping("/all")
    public List<Memo> getAll() {
        return new ArrayList<>(memos.values());
    }

    @PostMapping("/")
    public Memo addNew(@RequestBody Memo newMemo) {
        CosmosClient client = database.getCosmosClient();
        CosmosContainer container = client.getDatabase("memo").getContainer("memo-cards");
        newMemo.setId(nextId);

        container.createItem(
                newMemo,
                new PartitionKey(newMemo.getId()),
                new CosmosItemRequestOptions()
        );

        nextId++;

        return newMemo;
    }

    @PatchMapping("/coordinates")
    public void updateCoordinates(@RequestBody Memo updatedMemo) {
        try {
            memos.remove(updatedMemo.getId());
            List<Memo> updatedMemos = new ArrayList<>(memos.values());

            File file = new File(FILE_PATH);
            mapper.writeValue(file, updatedMemos);

        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to update coordinates of a memo");
        }
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable int id) {
        try {
            memos.remove(id);
            List<Memo> updatedMemos = new ArrayList<>(memos.values());

            File file = new File(FILE_PATH);
            mapper.writeValue(file, updatedMemos);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to delete a memo");
        }
    }
}
