// Copyright (c) Microsoft. All rights reserved.

package com.microsoft.azure.iotsolutions.devicetelemetry.services;

import com.google.inject.Inject;
import com.microsoft.azure.iotsolutions.devicetelemetry.services.runtime.IServicesConfig;
import play.libs.ws.WSClient;
import play.libs.ws.WSRequest;
import play.libs.ws.WSResponse;

import java.util.concurrent.CompletionStage;

public class KeyValueClient implements IKeyValueClient {

    private final int OK = 200;

    private final IServicesConfig servicesConfig;
    private String storageAdapterWebserviceUrl;

    private WSClient wsClient;

    @Inject
    public KeyValueClient(final IServicesConfig config, WSClient wsClient) {
        this.servicesConfig = config;
        this.storageAdapterWebserviceUrl = servicesConfig.getKeyValueStorageUrl();
        this.wsClient = wsClient;
    }

    public CompletionStage<Status> pingAsync() {
        WSRequest request = wsClient.url(storageAdapterWebserviceUrl + "/v1/status");
        CompletionStage<WSResponse> responsePromise = request.get();

        return responsePromise.handle((result, error) -> {
            if (error != null) {
                return new Status(false, error.getMessage());
            } else {
                return new Status(true, "Alive and well");
            }
        });
    }
}
