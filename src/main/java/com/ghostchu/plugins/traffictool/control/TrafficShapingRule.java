package com.ghostchu.plugins.traffictool.control;

public class TrafficShapingRule {
    private boolean bypass;
    private long writeLimit;
    private long readLimit;

    public TrafficShapingRule(boolean bypass, long writeLimit, long readLimit) {
        this.bypass = bypass;
        this.writeLimit = writeLimit;
        this.readLimit = readLimit;
    }

    public boolean isBypass() {
        return bypass;
    }

    public void setBypass(boolean bypass) {
        this.bypass = bypass;
    }

    public long getWriteLimit() {
        return writeLimit;
    }

    public void setWriteLimit(long writeLimit) {
        this.writeLimit = writeLimit;
    }

    public long getReadLimit() {
        return readLimit;
    }

    public void setReadLimit(long readLimit) {
        this.readLimit = readLimit;
    }
}
