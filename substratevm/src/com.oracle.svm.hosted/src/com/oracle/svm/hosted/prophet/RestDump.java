/**
 * Authors:
 * - Original Authors
 * - Vsevolod Pokhvalenko
 */

package com.oracle.svm.hosted.prophet;
import com.oracle.svm.hosted.prophet.model.Endpoint;
import com.oracle.svm.hosted.prophet.model.GraphQLCall;
import com.oracle.svm.hosted.prophet.model.GraphQLEndpoint;
import com.oracle.svm.hosted.prophet.model.RestCall;
import com.oracle.svm.hosted.prophet.model.WebsocketConnection;
import com.oracle.svm.hosted.prophet.model.WebsocketEndpoint;

import java.io.IOException;
import java.util.Set;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.RuntimeException;

import java.io.FileWriter;

public class RestDump {

    //RESTCALL CSV ORDER SHOULD BE
    //msName, restCallInClassName, parentMethod, uri, httpMethod, returnType, isPath, isBody, paramType, paramCount, isCollection
    //ENDPOINT CSV ORDER SHOULD BE
    //msName, endpointInClassName, parentMethod, arguments, path, httpMethod, returnType, isCollection
    //WebsocketConnection CSV ORDER SHOULD BE
    //msName, connectionInClassName, parentMethod, uri, returnType, param, isCollection

    //TO-DO: add header row to csv output!!!
    public void writeOutRestCalls(Set<RestCall> restCalls, String outputFile) {
        if (outputFile == null) {
            throw new RuntimeException("ProphetRestCallOutputFile option was not provided");
        }
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            for (RestCall rc : restCalls) {
                writer.write(rc.toString() + "\n");
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public void writeOutWebsocketConnections(Set<WebsocketConnection> websocketConnections, String outputFile) {
        if (outputFile == null) {
            throw new RuntimeException("ProphetWebsocketConnectionsOutputFile option was not provided");
        }
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            for (WebsocketConnection wc : websocketConnections) {
                writer.write(wc.toString() + "\n");
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public void writeOutWebsocketEndpoints(Set<WebsocketEndpoint> websocketEndpoints, String outputFile) {
        if (outputFile == null) {
            throw new RuntimeException("ProphetWebsocketEndpointOutputFile option was not provided");
        }
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            for (WebsocketEndpoint wc : websocketEndpoints) {
                writer.write(wc.toString() + "\n");
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public void writeOutEndpoints(Set<Endpoint> endpoints, String outputFile){
        if (outputFile == null){
            throw new RuntimeException("ProphetEndpointOutputFile option was not provided");
        }
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))){
            for (Endpoint ep : endpoints){
                writer.write(ep.toString() + "\n");
            }
        }catch(IOException ex){
            ex.printStackTrace();
        }
    }

    public void writeOutGraphQLEndpoints(Set<GraphQLEndpoint> endpoints, String outputFile){
        if (outputFile == null){
            throw new RuntimeException("ProphetGraphQLEndpointOutputFile option was not provided");
        }
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))){
            for (GraphQLEndpoint ep : endpoints){
                writer.write(ep.toString() + "\n");
            }
        }catch(IOException ex){
            ex.printStackTrace();
        }
    }

    public void writeOutGraphQLCalls(Set<GraphQLCall> graphQLCalls, String outputFile) {
        if (outputFile == null) {
            throw new RuntimeException("ProphetGraphQLCallOutputFile option was not provided");
        }
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            for (GraphQLCall rc : graphQLCalls) {
                writer.write(rc.toString() + "\n");
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
