package com.stockaggregator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Paths used by the one-time loader run (prefix {@code loader.*}). */
@ConfigurationProperties(prefix = "loader")
public class LoaderProperties {

    private String csv = "../data/stock_data.csv";
    private String schemaFile = "../schema.cql";

    public String getCsv() { return csv; }
    public void setCsv(String csv) { this.csv = csv; }

    public String getSchemaFile() { return schemaFile; }
    public void setSchemaFile(String schemaFile) { this.schemaFile = schemaFile; }
}
