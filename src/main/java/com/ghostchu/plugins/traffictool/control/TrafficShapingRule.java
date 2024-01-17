package com.ghostchu.plugins.traffictool.control;

import com.ghostchu.plugins.traffictool.TrafficTool;

public class TrafficShapingRule {
    private final long burstWriteLimit;
    private final long maxBurstDuration;
    private final long avgWriteLimit;
    private final long minAvgDuration;
    private boolean bypass;


    public TrafficShapingRule(boolean bypass, long avgWriteLimit, long avgDuration ,long burstWriteLimit, long burstDuration) {
        this.bypass = bypass;
        this.avgWriteLimit = avgWriteLimit;
        this.minAvgDuration = avgDuration;
        this.burstWriteLimit = burstWriteLimit;
        this.maxBurstDuration = burstDuration;
    }

    public boolean isBypass() {
        return bypass;
    }

    public void setBypass(boolean bypass) {
        this.bypass = bypass;
    }

    public long getAvgWriteLimit() {
        return avgWriteLimit;
    }

    public long getMaxBurstDuration() {
        return maxBurstDuration;
    }

    public long getMinAvgDuration() {
        return minAvgDuration;
    }

    public long getBurstWriteLimit() {
        return burstWriteLimit;
    }



    public String getDisplay() {
        StringBuilder builder = new StringBuilder();
        builder.append("突发写限制：").append(TrafficTool.humanReadableByteCount(burstWriteLimit, false)).append("\n");
        builder.append("最大持续突发时长：").append(getMaxBurstDuration()).append("\n");
        builder.append("非突发写限制：").append(TrafficTool.humanReadableByteCount(getAvgWriteLimit(),false)).append("\n");
        builder.append("最小持续非突发时长：").append(getMinAvgDuration()).append(" (整形检查间隔)").append("\n");
        if(bypass){
            builder.append("\n(管理员绕过已启用)");
        }
        return builder.toString();
    }
}
