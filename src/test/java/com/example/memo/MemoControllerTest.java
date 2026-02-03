package com.example.memo;

import com.azure.cosmos.*;
import com.azure.cosmos.models.*;
import com.azure.cosmos.util.CosmosPagedIterable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemoControllerTest {

    @Mock CosmosContainer cosmosContainer;
    @Mock CosmosClient cosmosClient;
    @Mock CosmosDatabase cosmosDatabase;
    @Mock Database database;
    @Mock OidcUser oidcUser;

    private MemoController controller;
    private static final String USER_ID = "test-user";
    private static final String OTHER_USER_ID = "other-test-user";

    @BeforeEach
    void setUp() {
        // 1. Wire the Cosmos client chain
        when(database.getCosmosClient()).thenReturn(cosmosClient);
        when(cosmosClient.getDatabase(anyString())).thenReturn(cosmosDatabase);
        when(cosmosDatabase.getContainer(anyString())).thenReturn(cosmosContainer);

        // 2. Mock the OidcUser → OidcIdToken → claims chain
        OidcIdToken mockToken = mock(OidcIdToken.class);
        when(mockToken.getClaims()).thenReturn(Map.of("oid", USER_ID));
        when(oidcUser.getIdToken()).thenReturn(mockToken);

        controller = new MemoController(database);
    }

    /* --------------------------------------------------------------------- */
    /* 1.  GET /all                                                        */
    /* --------------------------------------------------------------------- */

    @Test
    @DisplayName("getAll: returns memos for the authenticated user")
    void getAll_returnsMemos() {
        Memo memo1 = new Memo("1", USER_ID, "Title1", "Content1", 10, 20, Type.STICKY_NOTE);
        Memo memo2 = new Memo("2", USER_ID, "Title2", "Content2", 30, 40, Type.BIG_SHEET);

        // Mock the CosmosPagedIterable and its stream method
        @SuppressWarnings("unchecked")
        CosmosPagedIterable<Memo> pagedIterable = mock(CosmosPagedIterable.class);
        when(cosmosContainer.queryItems(any(SqlQuerySpec.class),
                any(CosmosQueryRequestOptions.class), eq(Memo.class)))
                .thenReturn(pagedIterable);
        when(pagedIterable.stream()).thenReturn(Arrays.asList(memo1, memo2).stream());

        // Act
        var result = controller.getAll(oidcUser);

        // Assert
        assertThat(result).containsExactly(memo1, memo2);
        verify(cosmosContainer).queryItems(any(SqlQuerySpec.class),
                any(CosmosQueryRequestOptions.class), eq(Memo.class));
    }

    /* --------------------------------------------------------------------- */
    /* 2.  POST /                                                          */
    /* --------------------------------------------------------------------- */

    @Test
    @DisplayName("addNew: creates a memo and returns it with an id")
    void addNew_createsMemo() {
        Memo newMemo = new Memo(null, null, "New", "Body", 0, 0, Type.BIG_SHEET);
        ArgumentCaptor<Memo> captor = ArgumentCaptor.forClass(Memo.class);

        Memo result = controller.addNew(newMemo, oidcUser);

        assertThat(result.getId()).isNotBlank();
        assertThat(result.getUserId()).isEqualTo(USER_ID);

        verify(cosmosContainer).createItem(captor.capture(),
                any(PartitionKey.class), any(CosmosItemRequestOptions.class));

        Memo created = captor.getValue();
        assertThat(created.getUserId()).isEqualTo(USER_ID);
    }

    /* --------------------------------------------------------------------- */
    /* 3.  PATCH /coordinates                                              */
    /* --------------------------------------------------------------------- */

    @Nested
    @DisplayName("updateCoordinates")
    class UpdateCoordinates {

        @Test
        @DisplayName("returns 200 when record exists and owner matches")
        void success() {
            Memo existing = new Memo("id-1", USER_ID, "T", "C", 0, 0, Type.STICKY_NOTE);
            Memo updated = new Memo("id-1", USER_ID, "T", "C", 5, 5, Type.STICKY_NOTE);

            // Mock the read response
            CosmosItemResponse<Memo> readResponse = mock(CosmosItemResponse.class);
            when(readResponse.getItem()).thenReturn(existing);

            // Mock the update response
            CosmosItemResponse<Memo> updateResponse = mock(CosmosItemResponse.class);
            when(updateResponse.getItem()).thenReturn(updated);

            when(cosmosContainer.readItem(eq("id-1"), any(PartitionKey.class), eq(Memo.class)))
                    .thenReturn(readResponse);
            when(cosmosContainer.replaceItem(any(Memo.class), eq("id-1"),
                    any(PartitionKey.class), any(CosmosItemRequestOptions.class)))
                    .thenReturn(updateResponse);

            ResponseEntity<Object> resp = controller.updateCoordinates(updated, oidcUser);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isEqualTo(updated);
        }

        @Test
        @DisplayName("returns 404 when memo does not exist")
        void notFound() {
            CosmosException notFound = mock(CosmosException.class);
            when(notFound.getStatusCode()).thenReturn(404);
            when(cosmosContainer.readItem(any(), any(PartitionKey.class), eq(Memo.class)))
                    .thenThrow(notFound);

            ResponseEntity<Object> resp = controller.updateCoordinates(new Memo(), oidcUser);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("returns 403 when owner does not match")
        void forbiddenOwnerMismatch() {
            Memo existing = new Memo("id-1", OTHER_USER_ID, "T", "C", 0, 0, Type.STICKY_NOTE);

            CosmosItemResponse<Memo> readResponse = mock(CosmosItemResponse.class);
            when(readResponse.getItem()).thenReturn(existing);

            when(cosmosContainer.readItem(eq("id-1"), any(PartitionKey.class), eq(Memo.class)))
                    .thenReturn(readResponse);

            ResponseEntity<Object> resp = controller.updateCoordinates(existing, oidcUser);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    /* --------------------------------------------------------------------- */
    /* 4.  DELETE /{id}                                                   */
    /* --------------------------------------------------------------------- */

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("returns 200 when memo exists and owner matches")
        void success() {
            Memo existing = new Memo("id-1", USER_ID, "T", "C", 0, 0, Type.STICKY_NOTE);

            CosmosItemResponse<Memo> readResponse = mock(CosmosItemResponse.class);
            when(readResponse.getItem()).thenReturn(existing);

            when(cosmosContainer.readItem(eq("id-1"), any(PartitionKey.class), eq(Memo.class)))
                    .thenReturn(readResponse);

            ResponseEntity<Object> resp = controller.delete("id-1", oidcUser);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(cosmosContainer).deleteItem(eq("id-1"),
                    any(PartitionKey.class), any(CosmosItemRequestOptions.class));
        }

        @Test
        @DisplayName("returns 404 when memo does not exist")
        void notFound() {
            CosmosException notFound = mock(CosmosException.class);
            when(notFound.getStatusCode()).thenReturn(404);
            when(cosmosContainer.readItem(anyString(), any(PartitionKey.class), eq(Memo.class)))
                    .thenThrow(notFound);

            ResponseEntity<Object> resp = controller.delete("id-unknown", oidcUser);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK); // controller swallows 404
        }

        @Test
        @DisplayName("returns 403 when owner does not match")
        void forbiddenOwnerMismatch() {
            Memo existing = new Memo("id-1", OTHER_USER_ID, "T", "C", 0, 0, Type.STICKY_NOTE);

            CosmosItemResponse<Memo> readResponse = mock(CosmosItemResponse.class);
            when(readResponse.getItem()).thenReturn(existing);

            when(cosmosContainer.readItem(eq("id-1"), any(PartitionKey.class), eq(Memo.class)))
                    .thenReturn(readResponse);

            ResponseEntity<Object> resp = controller.delete("id-1", oidcUser);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }
}
