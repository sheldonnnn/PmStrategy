package com.cmbc.mds.forex.subscription.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "init.subscription")
public class InitSubscriptionProperties {
    
    private SubscriptionConfig foreign = new SubscriptionConfig();
    private SubscriptionConfig dimple = new SubscriptionConfig();
    private SubscriptionConfig cmds = new SubscriptionConfig();

    public SubscriptionConfig getForeign() { return foreign; }
    public void setForeign(SubscriptionConfig foreign) { this.foreign = foreign; }

    public SubscriptionConfig getDimple() { return dimple; }
    public void setDimple(SubscriptionConfig dimple) { this.dimple = dimple; }

    public SubscriptionConfig getCmds() { return cmds; }
    public void setCmds(SubscriptionConfig cmds) { this.cmds = cmds; }

    public static class SubscriptionConfig {
        private List<String> symbols;
        private List<String> sources;
        private List<String> providers;

        public List<String> getSymbols() { return symbols; }
        public void setSymbols(List<String> symbols) { this.symbols = symbols; }

        public List<String> getSources() { return sources; }
        public void setSources(List<String> sources) { this.sources = sources; }

        public List<String> getProviders() { return providers; }
        public void setProviders(List<String> providers) { this.providers = providers; }
    }
}
