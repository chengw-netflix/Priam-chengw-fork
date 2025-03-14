/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.netflix.priam.resources;

import com.google.common.collect.Lists;
import com.netflix.priam.cluster.management.Compaction;
import com.netflix.priam.cluster.management.Flush;
import com.netflix.priam.compress.SnappyCompression;
import com.netflix.priam.config.IConfiguration;
import com.netflix.priam.connection.CassandraOperations;
import com.netflix.priam.connection.JMXConnectionException;
import com.netflix.priam.connection.JMXNodeTool;
import com.netflix.priam.defaultimpl.ICassandraProcess;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Do general operations. Start/Stop and some JMX node tool commands */
@Path("/v1/cassadmin")
@Produces(MediaType.APPLICATION_JSON)
public class CassandraAdmin {
    private static final String REST_HEADER_KEYSPACES = "keyspaces";
    private static final String REST_HEADER_TOKEN = "token";
    private static final String REST_SUCCESS = "[\"ok\"]";
    private static final Logger logger = LoggerFactory.getLogger(CassandraAdmin.class);
    private final IConfiguration config;
    private final ICassandraProcess cassProcess;
    private final Flush flush;
    private final Compaction compaction;
    private final CassandraOperations cassandraOperations;

    @Inject
    public CassandraAdmin(
            IConfiguration config,
            ICassandraProcess cassProcess,
            Flush flush,
            Compaction compaction,
            CassandraOperations cassandraOperations) {
        this.config = config;
        this.cassProcess = cassProcess;
        this.flush = flush;
        this.compaction = compaction;
        this.cassandraOperations = cassandraOperations;
    }

    @GET
    @Path("/start")
    public Response cassStart() throws IOException {
        cassProcess.start(true);
        return Response.ok(REST_SUCCESS, MediaType.APPLICATION_JSON).build();
    }

    @GET
    @Path("/stop")
    public Response cassStop(@DefaultValue("false") @QueryParam("force") boolean force)
            throws IOException {
        cassProcess.stop(force);
        return Response.ok(REST_SUCCESS, MediaType.APPLICATION_JSON).build();
    }

    @GET
    @Path("/refresh")
    public Response cassRefresh(@QueryParam(REST_HEADER_KEYSPACES) String keyspaces)
            throws IOException, ExecutionException, InterruptedException {
        logger.debug("node tool refresh is being called");
        if (StringUtils.isBlank(keyspaces))
            return Response.status(400).entity("Missing keyspace in request").build();

        JMXNodeTool nodeTool;
        try {
            nodeTool = JMXNodeTool.instance(config);
        } catch (JMXConnectionException e) {
            return Response.status(503).entity("JMXConnectionException").build();
        }
        nodeTool.refresh(Lists.newArrayList(keyspaces.split(",")));
        return Response.ok(REST_SUCCESS, MediaType.APPLICATION_JSON).build();
    }

    @GET
    @Path("/info")
    public Response cassInfo() throws JSONException {
        JMXNodeTool nodeTool;
        try {
            nodeTool = JMXNodeTool.instance(config);
        } catch (JMXConnectionException e) {
            return Response.status(503).entity("JMXConnectionException").build();
        }
        logger.debug("node tool info being called");
        return Response.ok(nodeTool.info(), MediaType.APPLICATION_JSON).build();
    }

    @GET
    @Path("/partitioner")
    public Response cassPartitioner() {
        JMXNodeTool nodeTool;
        try {
            nodeTool = JMXNodeTool.instance(config);
        } catch (JMXConnectionException e) {
            return Response.status(503).entity("JMXConnectionException").build();
        }
        logger.debug("node tool getPartitioner being called");
        return Response.ok(nodeTool.getPartitioner(), MediaType.TEXT_PLAIN).build();
    }

    @GET
    @Path("/ring/{id}")
    public Response cassRing(@PathParam("id") String keyspace) throws JSONException {
        JMXNodeTool nodeTool;
        try {
            nodeTool = JMXNodeTool.instance(config);
        } catch (JMXConnectionException e) {
            return Response.status(503).entity("JMXConnectionException").build();
        }
        logger.debug("node tool ring being called");
        return Response.ok(nodeTool.ring(keyspace), MediaType.APPLICATION_JSON).build();
    }

    @GET
    @Path("/status")
    public Response statusInfo() throws JSONException {
        JMXNodeTool nodeTool;
        try {
            nodeTool = JMXNodeTool.instance(config);
        } catch (JMXConnectionException e) {
            return Response.status(503).entity("JMXConnectionException").build();
        }
        logger.debug("node tool status being called");
        return Response.ok(nodeTool.statusInformation(), MediaType.APPLICATION_JSON).build();
    }

    @GET
    @Path("/flush")
    public Response cassFlush() {
        JSONObject rootObj = new JSONObject();

        try {
            flush.execute();
            rootObj.put("Flushed", true);
            return Response.ok().entity(rootObj).build();
        } catch (Exception e) {
            try {
                rootObj.put("status", "ERROR");
                rootObj.put("desc", e.getLocalizedMessage());
            } catch (Exception e1) {
                return Response.status(503).entity("FlushError").build();
            }
            return Response.status(503).entity(rootObj).build();
        }
    }

    @GET
    @Path("/compact")
    public Response cassCompact() {
        JSONObject rootObj = new JSONObject();

        try {
            compaction.execute();
            rootObj.put("Compacted", true);
            return Response.ok().entity(rootObj).build();
        } catch (Exception e) {
            try {
                rootObj.put("status", "ERROR");
                rootObj.put("desc", e.getLocalizedMessage());
            } catch (Exception e1) {
                return Response.status(503).entity("CompactionError").build();
            }
            return Response.status(503).entity(rootObj).build();
        }
    }

    @GET
    @Path("/cleanup")
    public Response cassCleanup() throws IOException, ExecutionException, InterruptedException {
        JMXNodeTool nodeTool;
        try {
            nodeTool = JMXNodeTool.instance(config);
        } catch (JMXConnectionException e) {
            return Response.status(503).entity("JMXConnectionException").build();
        }
        logger.debug("node tool cleanup being called");
        nodeTool.cleanup();
        return Response.ok().build();
    }

    @GET
    @Path("/repair")
    public Response cassRepair(
            @QueryParam("sequential") boolean isSequential,
            @QueryParam("localDC") boolean localDCOnly,
            @DefaultValue("false") @QueryParam("primaryRange") boolean primaryRange)
            throws IOException, ExecutionException, InterruptedException {
        JMXNodeTool nodeTool;
        try {
            nodeTool = JMXNodeTool.instance(config);
        } catch (JMXConnectionException e) {
            return Response.status(503).entity("JMXConnectionException").build();
        }
        logger.debug("node tool repair being called");
        nodeTool.repair(isSequential, localDCOnly, primaryRange);
        return Response.ok(REST_SUCCESS, MediaType.APPLICATION_JSON).build();
    }

    @GET
    @Path("/version")
    public Response version() {
        JMXNodeTool nodeTool;
        try {
            nodeTool = JMXNodeTool.instance(config);
        } catch (JMXConnectionException e) {
            return Response.status(503).entity("JMXConnectionException").build();
        }
        return Response.ok(
                        new JSONArray().put(nodeTool.getReleaseVersion()),
                        MediaType.APPLICATION_JSON)
                .build();
    }

    @GET
    @Path("/disablegossip")
    public Response disablegossip() {
        JMXNodeTool nodeTool;
        try {
            nodeTool = JMXNodeTool.instance(config);
        } catch (JMXConnectionException e) {
            return Response.status(503).entity("JMXConnectionException").build();
        }
        nodeTool.stopGossiping();
        return Response.ok(REST_SUCCESS, MediaType.APPLICATION_JSON).build();
    }

    @GET
    @Path("/enablegossip")
    public Response enablegossip() {
        JMXNodeTool nodeTool;
        try {
            nodeTool = JMXNodeTool.instance(config);
        } catch (JMXConnectionException e) {
            return Response.status(503).entity("JMXConnectionException").build();
        }
        nodeTool.startGossiping();
        return Response.ok(REST_SUCCESS, MediaType.APPLICATION_JSON).build();
    }

    @GET
    @Path("/disablethrift")
    public Response disablethrift() {
        JMXNodeTool nodeTool;
        try {
            nodeTool = JMXNodeTool.instance(config);
        } catch (JMXConnectionException e) {
            return Response.status(503).entity("JMXConnectionException").build();
        }
        nodeTool.stopThriftServer();
        return Response.ok(REST_SUCCESS, MediaType.APPLICATION_JSON).build();
    }

    @GET
    @Path("/enablethrift")
    public Response enablethrift() {
        JMXNodeTool nodeTool;
        try {
            nodeTool = JMXNodeTool.instance(config);
        } catch (JMXConnectionException e) {
            return Response.status(503).entity("JMXConnectionException").build();
        }
        nodeTool.startThriftServer();
        return Response.ok(REST_SUCCESS, MediaType.APPLICATION_JSON).build();
    }

    @GET
    @Path("/statusthrift")
    public Response statusthrift() throws JSONException {
        JMXNodeTool nodeTool;
        try {
            nodeTool = JMXNodeTool.instance(config);
        } catch (JMXConnectionException e) {
            return Response.status(503).entity("JMXConnectionException").build();
        }
        return Response.ok(
                        new JSONObject()
                                .put(
                                        "status",
                                        (nodeTool.isThriftServerRunning()
                                                ? "running"
                                                : "not running")),
                        MediaType.APPLICATION_JSON)
                .build();
    }

    @GET
    @Path("/gossipinfo")
    public Response gossipinfo() throws Exception {
        List<Map<String, String>> parsedInfo = cassandraOperations.gossipInfo();
        return Response.ok(parsedInfo, MediaType.APPLICATION_JSON).build();
    }

    @GET
    @Path("/move")
    public Response moveToken(@QueryParam(REST_HEADER_TOKEN) String newToken) throws IOException {
        JMXNodeTool nodeTool;
        try {
            nodeTool = JMXNodeTool.instance(config);
        } catch (JMXConnectionException e) {
            return Response.status(503).entity("JMXConnectionException").build();
        }
        nodeTool.move(newToken);
        return Response.ok(REST_SUCCESS, MediaType.APPLICATION_JSON).build();
    }

    @GET
    @Path("/drain")
    public Response cassDrain() throws IOException, ExecutionException, InterruptedException {
        JMXNodeTool nodeTool;
        try {
            nodeTool = JMXNodeTool.instance(config);
        } catch (JMXConnectionException e) {
            return Response.status(503).entity("JMXConnectionException").build();
        }
        logger.debug("node tool drain being called");
        nodeTool.drain();
        return Response.ok(REST_SUCCESS, MediaType.APPLICATION_JSON).build();
    }

    /*
    @parm in - absolute path on disk of compressed file.
    @param out - absolute path on disk for output, decompressed file
    @parapm compression algorithn -- optional and if not provided, defaults to Snappy
    */
    @GET
    @Path("/decompress")
    public Response decompress(@QueryParam("in") String in, @QueryParam("out") String out)
            throws Exception {
        SnappyCompression compress = new SnappyCompression();
        compress.decompressAndClose(new FileInputStream(in), new FileOutputStream(out));
        JSONObject object = new JSONObject();
        object.put("Input compressed file", in);
        object.put("Output decompress file", out);
        return Response.ok(object.toString(), MediaType.APPLICATION_JSON).build();
    }
}
