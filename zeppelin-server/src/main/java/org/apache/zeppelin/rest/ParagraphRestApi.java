package org.apache.zeppelin.rest;


import com.google.common.collect.Sets;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import org.apache.zeppelin.annotation.ZeppelinApi;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterNotFoundException;
import org.apache.zeppelin.interpreter.ManagedInterpreterGroup;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcess;
import org.apache.zeppelin.interpreter.util.SqlSplitter;
import org.apache.zeppelin.notebook.AuthorizationService;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.notebook.ParagraphTextParser;
import org.apache.zeppelin.rest.beans.AnalysisResult;
import org.apache.zeppelin.rest.exception.ForbiddenException;
import org.apache.zeppelin.rest.exception.NoteNotFoundException;
import org.apache.zeppelin.rest.exception.ParagraphNotFoundException;
import org.apache.zeppelin.server.JsonResponse;
import org.apache.zeppelin.service.AuthenticationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Path("/notebook")
@Produces("application/json")
public class ParagraphRestApi extends AbstractRestApi {

    private final Notebook notebook;
    private final AuthorizationService authorizationService;
    private final Logger LOGGER = LoggerFactory.getLogger(ParagraphRestApi.class);

    @Inject
    public ParagraphRestApi(
            Notebook notebook,
            AuthorizationService authorizationService,
            AuthenticationService authenticationService
    ) {
        super(authenticationService);
        this.notebook = notebook;
        this.authorizationService = authorizationService;
    }

    @GET
    @ZeppelinApi
    @Path("{noteId}/paragraph/{paragraphId}/analysis")
    public Response analysisParagraph(@PathParam("noteId") String noteId,
                                      @PathParam("paragraphId") String paragraphId) throws IOException, InterpreterNotFoundException {
        Paragraph paragraph = getParagraph(noteId, paragraphId);
        AnalysisResult analysisResult = new AnalysisResult(ParagraphTextParser.parse(paragraph.getText()));
        analysisResult.getScripts().addAll(new SqlSplitter().splitSql(analysisResult.getScriptText()));

        for (List<Interpreter> session : paragraph.getBindedInterpreter().getInterpreterGroup().values()) {
            for (Interpreter interpreter : session) {
                analysisResult.getConfigurations().putAll(interpreter.getProperties());
            }
        }

        return new JsonResponse<>(Response.Status.OK, analysisResult).build();
    }

    @GET
    @ZeppelinApi
    @Path("{noteId}/paragraph/{paragraphId}/reconnect")
    public Response reconnectParagraph(@PathParam("noteId") String noteId,
                                       @PathParam("paragraphId") String paragraphId,
                                       @QueryParam("includeProcess") Boolean reconnectProcess) throws IOException, InterpreterNotFoundException {
        if (reconnectProcess == null || !reconnectProcess) {
            LOGGER.info("Recovering paragraph : {}", paragraphId);
            getParagraph(noteId, paragraphId).recover();
            return new JsonResponse<>(Response.Status.OK, "OK").build();
        }

        LOGGER.info("Recovering remote interpreter process and paragraph : {}", paragraphId);
        Paragraph paragraph = getParagraph(noteId, paragraphId);
        Interpreter interpreter = paragraph.getBindedInterpreter();

        RemoteInterpreterProcess remoteInterpreterProcess = getRemoteInterpreterProcess(interpreter);
        if (remoteInterpreterProcess == null || !remoteInterpreterProcess.reconnect()) {
            LOGGER.info("Failed to recover remote interpreter process, and skip paragraph recovering.");
            return new JsonResponse<>(Response.Status.OK, "ERROR").build();
        }

        paragraph.recover(); // Only recover when remote process recovered.
        return new JsonResponse<>(Response.Status.OK, "OK").build();
    }

    private RemoteInterpreterProcess getRemoteInterpreterProcess(Interpreter interpreter) throws IOException {
        if (interpreter == null) {
            return null;
        }

        if (interpreter.getInterpreterGroup() == null) {
            return null;
        }


        if (!(interpreter.getInterpreterGroup() instanceof ManagedInterpreterGroup)) {
            return null;
        }

        ManagedInterpreterGroup interpreterGroup = (ManagedInterpreterGroup) interpreter.getInterpreterGroup();
        return interpreterGroup.getOrCreateInterpreterProcess(interpreter.getUserName(), interpreter.getProperties());
    }

    private Paragraph getParagraph(String noteId, String paragraphId) throws IOException {
        Note note = notebook.processNote(noteId, noteToProcess -> noteToProcess);
        checkIfNoteIsNotNull(note, noteId);
        checkIfUserCanRead(noteId);

        Paragraph paragraph = note.getParagraph(paragraphId);
        checkIfParagraphIsNotNull(paragraph, paragraphId);

        return paragraph;
    }

    private void checkIfNoteIsNotNull(Note note, String noteId) {
        if (note == null) {
            throw new NoteNotFoundException(noteId);
        }
    }

    private void checkIfParagraphIsNotNull(Paragraph paragraph, String paragraphId) {
        if (paragraph == null) {
            throw new ParagraphNotFoundException(paragraphId);
        }
    }

    /**
     * Check if the current user can access (at least he have to be reader) the given note.
     */
    private void checkIfUserCanRead(String noteId) {
        Set<String> userAndRoles = Sets.newHashSet();
        userAndRoles.add(authenticationService.getPrincipal());
        userAndRoles.addAll(authenticationService.getAssociatedRoles());
        if (!authorizationService.hasReadPermission(userAndRoles, noteId)) {
            throw new ForbiddenException("Insufficient privileges you cannot get this paragraph");
        }
    }

}
