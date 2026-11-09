package org.folio.tlib.example.data;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.vertx.sqlclient.Row;
import java.util.UUID;

/**
 * Book object.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Book {

  private UUID id;

  private String title;

  private String indexTitle;

  /**
   * Map a database row to a book.
   */
  public static Book fromRow(Row row) {
    Book book = new Book();
    book.setId(row.get(UUID.class, "id"));
    book.setTitle(row.getString("title"));
    book.setIndexTitle(row.getString("index_title"));
    return book;
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getIndexTitle() {
    return indexTitle;
  }

  public void setIndexTitle(String indexTitle) {
    this.indexTitle = indexTitle;
  }
}
