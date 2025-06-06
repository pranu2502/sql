/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.plugin.rest;

import static org.opensearch.core.rest.RestStatus.INTERNAL_SERVER_ERROR;

import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestController;
import org.opensearch.rest.RestRequest;
import org.opensearch.sql.common.utils.QueryContext;
import org.opensearch.sql.datasources.utils.Scheduler;
import org.opensearch.sql.legacy.executor.format.ErrorMessageFactory;
import org.opensearch.sql.legacy.metrics.Metrics;
import org.opensearch.transport.client.node.NodeClient;

/** PPL Node level status. */
public class RestPPLStatsAction extends BaseRestHandler {

  private static final Logger LOG = LogManager.getLogger(RestPPLStatsAction.class);

  /** API endpoint path. */
  public static final String PPL_STATS_API_ENDPOINT = "/_plugins/_ppl/stats";

  public RestPPLStatsAction(Settings settings, RestController restController) {
    super();
  }

  @Override
  public String getName() {
    return "ppl_stats_action";
  }

  @Override
  public List<Route> routes() {
    return ImmutableList.of(
        new Route(RestRequest.Method.POST, PPL_STATS_API_ENDPOINT),
        new Route(RestRequest.Method.GET, PPL_STATS_API_ENDPOINT));
  }

  @Override
  protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) {

    QueryContext.addRequestId();

    try {
      return channel ->
          Scheduler.schedule(
              client,
              () ->
                  channel.sendResponse(
                      new BytesRestResponse(RestStatus.OK, Metrics.getInstance().collectToJSON())));
    } catch (Exception e) {
      LOG.error("Failed during Query PPL STATS Action.", e);

      return channel ->
          channel.sendResponse(
              new BytesRestResponse(
                  INTERNAL_SERVER_ERROR,
                  ErrorMessageFactory.createErrorMessage(e, INTERNAL_SERVER_ERROR.getStatus())
                      .toString()));
    }
  }

  @Override
  protected Set<String> responseParams() {
    Set<String> responseParams = new HashSet<>(super.responseParams());
    responseParams.addAll(Arrays.asList("format", "sanitize"));
    return responseParams;
  }
}
