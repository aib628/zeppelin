package org.apache.zeppelin.rest.beans;

import com.google.gson.Gson;
import java.util.Set;

//{"principal":"admin","ticket":"d348309d-0c0a-4c49-bb0e-f8f58a71058d","roles":"[\"admin\"]"}
public class ZeppelinTicket {

    private String url;
    private String principal;
    private String ticket;
    private Set<String> roles;

    private static final Gson GSON = new Gson();

    public static ZeppelinTicket fromJson(String json) {
        return GSON.fromJson(json, ZeppelinTicket.class);
    }

    public boolean ticketEquals(ZeppelinTicket zeppelinTicket) {
        return this.ticket.equals(zeppelinTicket.getTicket());
    }

    public ZeppelinTicket url(String url) {
        setUrl(url);
        return this;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getPrincipal() {
        return principal;
    }

    public void setPrincipal(String principal) {
        this.principal = principal;
    }

    public String getTicket() {
        return ticket;
    }

    public void setTicket(String ticket) {
        this.ticket = ticket;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles;
    }
}
