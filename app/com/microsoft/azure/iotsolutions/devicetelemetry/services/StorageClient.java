// Copyright (c) Microsoft. All rights reserved

package com.microsoft.azure.iotsolutions.devicetelemetry.services;

import com.google.inject.Inject;
import com.microsoft.azure.documentdb.*;
import com.microsoft.azure.iotsolutions.devicetelemetry.services.exceptions.InvalidConfigurationException;
import com.microsoft.azure.iotsolutions.devicetelemetry.services.runtime.IServicesConfig;
import play.Logger;
import play.mvc.Http;

import java.net.URI;

public class StorageClient implements IStorageClient {

    private static final Logger.ALogger log = Logger.of(StorageClient.class);

    private final IServicesConfig servicesConfig;

    private String storageHostName;
    private String storagePrimaryKey;

    private DocumentClient client;
    private Database telemetryDb;

    @Inject
    public StorageClient(final IServicesConfig config) throws Exception {
        this.servicesConfig = config;
        parseConnectionString();
        this.client = getDocumentClient();

        CreateDatabaseIfNotExists();
    }

    // returns existing document client, creates document client if null
    public DocumentClient getDocumentClient() throws InvalidConfigurationException {
        if (this.client == null) {
            this.client = new DocumentClient(
                storageHostName,
                storagePrimaryKey,
                ConnectionPolicy.GetDefault(),
                ConsistencyLevel.Session);

            if (this.client == null) {
                // TODO add logging if connection fails (don't log connection string)
                log.error("Could not connect to DocumentClient");
                throw new InvalidConfigurationException("Could not connect to DocumentClient");
            }
        }

        return this.client;
    }

    @Override
    public ResourceResponse<DocumentCollection> createCollectionIfNotExists(String id) throws Exception {
        DocumentCollection collectionInfo = new DocumentCollection();
        RangeIndex index = Index.Range(DataType.String, -1);
        collectionInfo.setIndexingPolicy(new IndexingPolicy(new Index[]{index}));
        collectionInfo.setId(id);

        // Azure Cosmos DB collections can be reserved with throughput specified in request units/second.
        // Here we create a collection with 400 RU/s.
        RequestOptions requestOptions = new RequestOptions();
        requestOptions.setOfferThroughput(400);
        String dbUrl = "/dbs/" + this.telemetryDb.getId();
        String colUrl = dbUrl + "/colls/" + id;
        boolean create = false;
        ResourceResponse<DocumentCollection> response = null;

        try {
            response = this.client.readCollection(colUrl, requestOptions);
        } catch (DocumentClientException dcx) {
            if (dcx.getStatusCode() == Http.Status.NOT_FOUND) {
                create = true;
            } else {
                log.error("Error reading collection: " + id, dcx);
            }
        }

        if (create) {
            try {
                response = this.client.createCollection(dbUrl, collectionInfo, requestOptions);
            } catch (Exception ex) {
                log.error("Error creating collection: " + id, ex);
                throw ex;
            }
        }

        return response;
    }

    @Override
    public ResourceResponse<Document> upsertDocument(String colId, final Object document) throws Exception {
        String colUrl = String.format("/dbs/%s/colls/%s", this.telemetryDb.getId(), colId);
        try {
            return this.client.upsertDocument(colUrl, document, new RequestOptions(), false);
        } catch (Exception ex) {
            log.error("Error upserting document collection: " + colId, ex);
            throw ex;
        }
    }

    @Override
    public ResourceResponse<Document> deleteDocument(String colId, String docId) throws Exception {
        String docUrl = String.format("/dbs/%s/colls/%s/docs/%s", this.telemetryDb.getId(), colId, docId);
        try {
            return this.client.deleteDocument(docUrl, new RequestOptions());
        } catch (Exception ex) {
            log.error("Error deleting document in collection: " + colId, ex);
            throw ex;
        }
    }

    @Override
    public FeedResponse<Document> queryDocuments(String colId, FeedOptions queryOptions, String queryString) throws Exception {
        if (queryOptions == null) {
            queryOptions = new FeedOptions();
            queryOptions.setPageSize(-1);
            queryOptions.setEnableCrossPartitionQuery(true);
        }

        String collectionLink = String.format("/dbs/%s/colls/%s", this.telemetryDb.getId(), colId);
        FeedResponse<Document> queryResults = this.client.queryDocuments(
            collectionLink,
            queryString, queryOptions);

        return queryResults;
    }

    @Override
    public Status Ping() {
        URI response = null;

        if (this.client != null) {
            response = this.client.getReadEndpoint();
        }

        if (response != null) {
            return new Status(
                true,
                "Alive and Well!");
        } else {
            return new Status(
                false,
                "Could not reach storage service" +
                    "Check connection string");
        }
    }

    // splits connection string into hostname and primary key
    private void parseConnectionString() throws InvalidConfigurationException {
        final String HOST_ID = "AccountEndpoint=";
        final String KEY_ID = "AccountKey=";

        String connectionString = servicesConfig.getStorageConnectionString();

        if (!connectionString.contains(";") ||
            !connectionString.contains(HOST_ID) ||
            !connectionString.contains(KEY_ID)) {
            // TODO add logging for connection string error (don't log conn string)
            throw new InvalidConfigurationException("Connection string format: " +
                "accepted format \"AccountEndpoint={value};AccountKey={value}\"");
        }

        String[] params = connectionString.split(";");
        if (params.length > 1) {
            this.storageHostName = params[0].substring(
                params[0].indexOf(HOST_ID) + HOST_ID.length());

            this.storagePrimaryKey = params[1].substring(
                params[1].indexOf(KEY_ID) + KEY_ID.length());
        } else {
            // TODO add logging for connection string error (don't log conn string)
            throw new InvalidConfigurationException("Connection string format error");
        }
    }

    private ResourceResponse<Database> CreateDatabaseIfNotExists() throws Exception {
        this.telemetryDb = new Database();
        this.telemetryDb.setId("telemetry");

        String dbUrl = "/dbs/" + this.telemetryDb.getId();
        boolean create = false;
        ResourceResponse<Database> response = null;

        try {
            response = this.client.readDatabase(dbUrl, null);
        } catch (DocumentClientException dcx) {
            if (dcx.getStatusCode() == Http.Status.NOT_FOUND) {
                create = true;
            } else {
                log.error("Error reading database: " + this.telemetryDb.getId(), dcx);
            }
        }

        if (create) {
            try {
                response = this.client.createDatabase(this.telemetryDb, null);
            } catch (Exception ex) {
                log.error("Error creating database: " + this.telemetryDb.getId(), ex);
                throw ex;
            }
        }

        return response;
    }
}
