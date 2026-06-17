package com.stockaggregator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

/** Cassandra connection settings (prefix {@code cassandra.*}). */
@ConfigurationProperties(prefix = "cassandra")
public class CassandraProperties {

    /** Comma-separated contact points, e.g. "127.0.0.1" or "node1,node2". */
    private String hosts = "127.0.0.1";
    private int port = 9042;
    private String keyspace = "stock_keyspace";
    private String localDatacenter = "datacenter1";
    private String username;
    private String password;

    public List<String> hostList() {
        return Arrays.stream(hosts.split(","))
                .map(String::trim)
                .filter(h -> !h.isEmpty())
                .toList();
    }

    public String getHosts() { return hosts; }
    public void setHosts(String hosts) { this.hosts = hosts; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public String getKeyspace() { return keyspace; }
    public void setKeyspace(String keyspace) { this.keyspace = keyspace; }

    public String getLocalDatacenter() { return localDatacenter; }
    public void setLocalDatacenter(String localDatacenter) { this.localDatacenter = localDatacenter; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
