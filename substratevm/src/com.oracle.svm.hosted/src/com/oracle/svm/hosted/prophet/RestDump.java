package com.oracle.svm.hosted.prophet;
import com.oracle.svm.hosted.prophet.model.Endpoint;
import com.oracle.svm.hosted.prophet.model.RestCall;
import com.oracle.svm.hosted.prophet.model.WebsocketConnection;

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
            // Write the header row
            writer.write("msName,restCallInClassName,parentMethod,uri,httpMethod,returnType,isPath,isBody,paramType,paramCount,isCollection\n");
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
            // Write the header row
            writer.write("msName, connectionInClassName, parentMethod, uri, returnType, param, isCollection\n");
            for (WebsocketConnection wc : websocketConnections) {
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
            writer.write("msName,endpointInClassName,parentMethod,arguments,path,httpMethod,returnType,isCollection\n");
            for (Endpoint ep : endpoints){
                writer.write(ep.toString() + "\n");
            }
        }catch(IOException ex){
            ex.printStackTrace();
        }
    }
}
