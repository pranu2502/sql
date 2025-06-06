/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.legacy;

import static org.hamcrest.Matchers.equalTo;

import java.io.IOException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Ignore;
import org.junit.Test;

@Ignore(
    "OpenSearch DSL format is deprecated in 3.0.0. Ignore legacy IT that relies on json format"
        + " response for now. Need to decide what to do with these test cases.")
public class OrderIT extends SQLIntegTestCase {

  @Override
  protected void init() throws Exception {
    loadIndex(Index.ORDER);
  }

  @Test
  public void simpleOrder() throws IOException {
    String query = "SELECT id, name FROM opensearch-sql_test_index_order ORDER BY id";
    JSONArray result = getSortExplain(query);
    assertThat(result.length(), equalTo(1));
    JSONObject jsonObject = getSortByField(result, "id");
    assertThat(query(result, "/0/id/order"), equalTo("asc"));
    assertFalse(jsonObject.getJSONObject("id").has("missing"));
  }

  @Test
  public void orderByScore() throws IOException {
    String query = "SELECT * FROM opensearch-sql_test_index_order ORDER BY _score";
    JSONArray result = getSortExplain(query);
    assertThat(result.length(), equalTo(1));
    JSONObject jsonObject = getSortByField(result, "_score");
    assertThat(query(result, "/0/_score/order"), equalTo("asc"));
    assertFalse(jsonObject.getJSONObject("_score").has("missing"));

    JSONObject response = executeQuery(query);
    JSONArray hits = getHits(response);
    assertThat(hits.length(), equalTo(7));
  }

  @Test
  public void simpleOrderMultipleFields() throws IOException {
    String query = "SELECT id, name FROM opensearch-sql_test_index_order ORDER BY id, name";
    JSONArray result = getSortExplain(query);
    assertThat(result.length(), equalTo(2));
    assertTrue(result.getJSONObject(0).has("id"));
    assertTrue(result.getJSONObject(1).has("name.keyword"));
  }

  @Test
  public void explicitOrderType() throws IOException {
    String query =
        "SELECT id, name FROM opensearch-sql_test_index_order ORDER BY id ASC, name DESC";
    JSONArray result = getSortExplain(query);
    assertThat(result.length(), equalTo(2));
    assertThat(query(result, "/0/id/order"), equalTo("asc"));
    assertThat(query(result, "/1/name.keyword/order"), equalTo("desc"));
  }

  @Test
  public void orderByIsNull() throws IOException {
    String query = "SELECT * FROM opensearch-sql_test_index_order ORDER BY id IS NULL, id DESC";
    JSONArray result = getSortExplain(query);
    assertThat(result.length(), equalTo(1));
    assertThat(query(result, "/0/id/order"), equalTo("desc"));
    assertThat(query(result, "/0/id/missing"), equalTo("_last"));

    JSONObject response = executeQuery(query);
    JSONArray hits = getHits(response);
    assertThat(query(hits, "/0/_source/id"), equalTo("5"));

    // Another equivalent syntax
    assertThat(
        explainQuery("SELECT * FROM opensearch-sql_test_index_order ORDER BY id IS NULL, id DESC"),
        equalTo(
            explainQuery(
                "SELECT * FROM opensearch-sql_test_index_order ORDER BY id IS NULL DESC")));
  }

  @Test
  public void orderByIsNotNull() throws IOException {
    String query = "SELECT id, name FROM opensearch-sql_test_index_order ORDER BY name IS NOT NULL";
    JSONArray result = getSortExplain(query);
    assertThat(1, equalTo(result.length()));
    assertThat(query(result, "/0/name.keyword/order"), equalTo("asc"));
    assertThat(query(result, "/0/name.keyword/missing"), equalTo("_first"));

    JSONObject response = executeQuery(query);
    JSONArray hits = getHits(response);
    assertFalse(hits.getJSONObject(0).getJSONObject("_source").has("name"));
    assertThat(
        hits.getJSONObject(hits.length() - 1).query("/_source/name").toString(), equalTo("f"));

    // Another equivalent syntax
    assertThat(
        explainQuery(
            "SELECT id, name FROM opensearch-sql_test_index_order ORDER BY name IS NOT NULL"),
        equalTo(
            explainQuery(
                "SELECT id, name FROM opensearch-sql_test_index_order "
                    + "ORDER BY name IS NOT NULL ASC")));
  }

  @Test
  public void multipleOrderByWithNulls() throws IOException {
    String query =
        "SELECT id, name FROM opensearch-sql_test_index_order ORDER BY id IS NULL, name IS NOT"
            + " NULL";
    JSONArray result = getSortExplain(query);
    assertThat(result.length(), equalTo(2));
    assertThat(query(result, "/0/id/missing"), equalTo("_last"));
    assertThat(query(result, "/1/name.keyword/missing"), equalTo("_first"));
  }

  @Test
  public void testOrderByMergeForSameField() throws IOException {
    String query =
        "SELECT * FROM opensearch-sql_test_index_order "
            + "ORDER BY id IS NULL, name DESC, id DESC, id IS NOT NULL, name IS NULL";
    JSONArray result = getSortExplain(query);
    assertThat(2, equalTo(result.length()));
    assertThat(query(result, "/0/id/order"), equalTo("asc"));
    assertThat(query(result, "/0/id/missing"), equalTo("_first"));
    assertThat(query(result, "/1/name.keyword/order"), equalTo("asc"));
    assertThat(query(result, "/1/name.keyword/missing"), equalTo("_last"));
  }

  private JSONArray getSortExplain(String sqlQuery) throws IOException {
    String result = explainQuery(sqlQuery);
    JSONObject jsonObject = new JSONObject(result);
    return jsonObject.getJSONArray("sort");
  }

  private JSONObject getSortByField(JSONArray sortArray, String orderByName) {
    JSONObject jsonObject;
    for (int i = 0; i < sortArray.length(); i++) {
      jsonObject = (JSONObject) sortArray.get(i);
      if (jsonObject.has(orderByName)) {
        return jsonObject;
      }
    }
    return null;
  }

  private String query(JSONArray jsonArray, String jsonPath) {
    return jsonArray.query(jsonPath).toString();
  }
}
