package com.example.memo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.web.bind.annotation.*;

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

    private final ObjectMapper mapper = new ObjectMapper();
    private final String FILE_PATH = "src/main/resources/memos.json";

    private HashMap<Integer, Memo> memos = new HashMap<>();
    private int nextId = 0;

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
            for (Memo memo : allMemos) {
                nextId = max(nextId, memo.getId());
                memos.put(memo.getId(), memo);
            }
            nextId++;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @GetMapping("/all")
    public List<Memo> getAll() {
        return new ArrayList<>(memos.values());
    }

    @PatchMapping("/coordinates")
    public void updateCoordinates(@RequestBody Memo updatedMemo) {
        try {
            memos.remove(updatedMemo.getId());
            memos.put(updatedMemo.getId(), updatedMemo);
            List<Memo> updatedMemos = new ArrayList<>(memos.values());

            File file = new File(FILE_PATH);
            mapper.writeValue(file, updatedMemos);

        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to update coordinates of a memo");
        }
    }

    @PostMapping("/")
    public Memo addNew(@RequestBody Memo newMemo) {
        try {
            File file = new File(FILE_PATH);

            newMemo.setId(nextId);
            memos.put(nextId, newMemo);
            nextId++;

            List<Memo> memos = mapper.readValue(file, new TypeReference<>() {});
            memos.add(newMemo);
            mapper.writeValue(file, memos);

            return newMemo;
        } catch (IOException e) {
            nextId--;
            e.printStackTrace();
            throw new RuntimeException("Failed to save memo");
        }
    }
}
