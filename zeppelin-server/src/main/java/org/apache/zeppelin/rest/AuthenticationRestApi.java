package org.apache.zeppelin.rest;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.annotation.ZeppelinApi;
import org.apache.zeppelin.rest.beans.ZeppelinTicket;
import org.apache.zeppelin.server.JsonResponse;
import org.apache.zeppelin.service.AuthenticationService;
import org.apache.zeppelin.ticket.TicketContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Path("/security")
@Produces("application/json")
public class AuthenticationRestApi extends AbstractRestApi {

    private final Logger LOGGER = LoggerFactory.getLogger(ParagraphRestApi.class);

    @Inject
    protected AuthenticationRestApi(AuthenticationService authenticationService) {
        super(authenticationService);
    }

    @POST
    @ZeppelinApi
    @Path("ticket/check")
    public Response checkTicket(String message) {
        ZeppelinTicket zeppelinTicket = ZeppelinTicket.fromJson(message);
        TicketContainer.Entry ticketEntry = TicketContainer.instance.getTicketEntry(zeppelinTicket.getPrincipal());
        if (ticketEntry == null || StringUtils.isEmpty(ticketEntry.getTicket())) {
            LOGGER.info("Invalid principal : {}", zeppelinTicket.getPrincipal());
            return new JsonResponse<>(Response.Status.OK, false).build();
        } else if (!ticketEntry.getTicket().equals(zeppelinTicket.getTicket())) {
            LOGGER.info("Invalid ticket {} : {}", zeppelinTicket.getPrincipal(), zeppelinTicket.getTicket());
            return new JsonResponse<>(Response.Status.OK, false).build();
        }

        return new JsonResponse<>(Response.Status.OK, true).build();
    }
}
