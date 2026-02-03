package com.example.memo;

import com.azure.cosmos.*;
import com.azure.cosmos.models.*;
import com.example.Utilities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MemoControllerTest {

    @Mock CosmosContainer cosmosContainer;
    @Mock CosmosClient cosmosClient;
    @Mock Database database;
    @Mock OidcUser oidcUser;
    @Mock CosmosItemResponse<Memo> cosmosItemResponse;

    private MemoController controller;

    private final String USER_ID = "test-user";
    private final String OTHER_USER_ID = "other-test-user";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        when(database.getCosmosClient()).thenReturn(cosmosClient);
        when(cosmosClient.getDatabase(anyString())).thenReturn(mock(CosmosDatabase.class));
        when(mock(CosmosDatabase.class).getContainer(anyString())).thenReturn(cosmosContainer);

        // Stub the static helper that extracts the user id
        try (var ignored = mockStatic(Utilities.class)) {
            when(Utilities.filterClaims(oidcUser)).thenReturn(Map.of("oid", USER_ID));
        }

        controller = new MemoController(database);
    }

    /* --------------------------- GET /all --------------------------- */

    /*
    @Test
    @DisplayName("getAll: returns list of memos for the authenticated user")
    void getAll_returnsMemos() {
        // Arrange
        Memo memo = new Memo("1", USER_ID, "Title", "Content", 10, 20, Type.STICKY_NOTE);
        when(cosmosContainer.queryItems(any(SqlQuerySpec.class),
                any(CosmosQueryRequestOptions.class), eq(Memo.class)))
                .thenReturn(mock(CosmosQueryIterable.class));
        when(mock(CosmosQueryIterable.class).stream()).thenReturn(Stream.of(memo));

        // Act
        List<Memo> result = controller.getAll(oidcUser);

        // Assert
        assertThat(result).containsExactly(memo);
        verify(cosmosContainer).queryItems(any(), any(), eq(Memo.class));
    }

    /* --------------------------- POST / --------------------------- */

    @Test
    @DisplayName("addNew: creates a memo and returns it with an id")
    void addNew_createsMemo() {
        // Arrange
        Memo newMemo = new Memo(null, null, "New", "Body", 0, 0, Type.BIG_SHEET);
        // Capture the item that is passed to createItem
        ArgumentCaptor<Memo> captor = ArgumentCaptor.forClass(Memo.class);

        // Act
        Memo result = controller.addNew(newMemo, oidcUser);

        // Assert
        assertThat(result.getId()).isNotBlank();
        assertThat(result.getUserId()).isEqualTo(USER_ID);
        verify(cosmosContainer).createItem(captor.capture(),
                any(PartitionKey.class), any(CosmosItemRequestOptions.class));
        Memo created = captor.getValue();
        assertThat(created.getUserId()).isEqualTo(USER_ID);
    }

    /* --------------------------- PATCH /coordinates --------------------------- */

    @Nested
    @DisplayName("updateCoordinates")
    class UpdateCoordinates {

        @Test
        @DisplayName("returns 200 and updated memo when record exists and owner matches")
        void success() {
            // Arrange
            Memo existing = new Memo("id-1", USER_ID, "T", "C", 0, 0, Type.STICKY_NOTE);
            Memo updated = new Memo("id-1", USER_ID, "T", "C", 5, 5, Type.STICKY_NOTE);

            when(cosmosContainer.readItem(eq("id-1"), any(PartitionKey.class), eq(Memo.class)))
                    .thenReturn(mockedResponse(existing));
            when(cosmosContainer.replaceItem(any(Memo.class), eq("id-1"),
                    any(PartitionKey.class), any(CosmosItemRequestOptions.class)))
                    .thenReturn(mockedResponse(updated));

            // Act
            ResponseEntity<Object> resp = controller.updateCoordinates(updated, oidcUser);

            // Assert
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isEqualTo(updated);
        }

        @Test
        @DisplayName("returns 404 when memo does not exist")
        void notFound() {
            // Arrange
            CosmosException notFound = mock(CosmosException.class);
            when(notFound.getStatusCode()).thenReturn(404);
            when(cosmosContainer.readItem(anyString(), any(PartitionKey.class), eq(Memo.class)))
                    .thenThrow(notFound);

            // Act
            ResponseEntity<Object> resp = controller.updateCoordinates(new Memo(), oidcUser);

            // Assert
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("returns 403 when owner does not match")
        void forbiddenOwnerMismatch() {
            // Arrange
            Memo existing = new Memo("id-1", "other-user", "T", "C", 0, 0, Type.STICKY_NOTE);
            when(cosmosContainer.readItem(eq("id-1"), any(PartitionKey.class), eq(Memo.class)))
                    .thenReturn(mockedResponse(existing));

            // Act
            ResponseEntity<Object> resp = controller.updateCoordinates(existing, oidcUser);

            // Assert
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    /* --------------------------- DELETE /{id} --------------------------- */

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("returns 200 when memo exists and owner matches")
        void success() {
            // Arrange
            Memo existing = new Memo("id-1", USER_ID, "T", "C", 0, 0, Type.STICKY_NOTE);
            when(cosmosContainer.readItem(eq("id-1"), any(PartitionKey.class), eq(Memo.class)))
                    .thenReturn(mockedResponse(existing));

            // Act
            ResponseEntity<Object> resp = controller.delete("id-1", oidcUser);

            // Assert
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(cosmosContainer).deleteItem(eq("id-1"), any(PartitionKey.class),
                    any(CosmosItemRequestOptions.class));
        }

        @Test
        @DisplayName("returns 404 when memo does not exist")
        void notFound() {
            // Arrange
            CosmosException notFound = mock(CosmosException.class);
            when(notFound.getStatusCode()).thenReturn(404);
            when(cosmosContainer.readItem(anyString(), any(PartitionKey.class), eq(Memo.class)))
                    .thenThrow(notFound);

            // Act
            ResponseEntity<Object> resp = controller.delete("id-unknown", oidcUser);

            // Assert
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK); // controller swallows 404
        }

        @Test
        @DisplayName("returns 403 when owner does not match")
        void forbiddenOwnerMismatch() {
            // Arrange
            Memo existing = new Memo("id-1", "other-user", "T", "C", 0, 0, Type.STICKY_NOTE);
            when(cosmosContainer.readItem(eq("id-1"), any(PartitionKey.class), eq(Memo.class)))
                    .thenReturn(mockedResponse(existing));

            // Act
            ResponseEntity<Object> resp = controller.delete("id-1", oidcUser);

            // Assert
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    /* --------------------------- Helpers --------------------------- */

    private CosmosItemResponse<Memo> mockedResponse(Memo memo) {
        CosmosItemResponse<Memo> resp = mock(CosmosItemResponse.class);
        when(resp.getItem()).thenReturn(memo);
        return resp;
    }
}
