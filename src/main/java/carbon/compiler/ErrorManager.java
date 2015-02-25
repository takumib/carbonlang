/*
 * [The "BSD license"]
 * Copyright (c) 2014 Takumi Bolte, Dan Welch
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * 3. The name of the author may not be used to endorse or promote products
 * derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package carbon.compiler;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.tool.ErrorSeverity;
import org.antlr.v4.tool.GrammarSyntaxMessage;
import org.stringtemplate.v4.ST;
import org.stringtemplate.v4.STGroup;
import org.stringtemplate.v4.STGroupFile;

import java.io.File;
import java.util.EnumSet;
import java.util.Set;

public class ErrorManager extends BaseErrorListener {

    public static final String FORMATS_DIR =  "carbon/templates/messages/";

    private final STGroup format =
            new STGroupFile(FORMATS_DIR
                    + "carbon" + STGroup.GROUP_FILE_EXTENSION);

    private final CarbonCompiler compiler;
    private int errorCount, warningCount;

    public Set<ErrorKind> errorTypes = EnumSet.noneOf(ErrorKind.class);

    public ErrorManager(CarbonCompiler c) {
        this.compiler = c;
    }

    public int getWarningCount() {
        return warningCount;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public void resetCounts() {
        warningCount = 0;
        errorCount = 0;
    }

    public ST getMessageTemplate(CarbonMessage msg) {
        ST messageST = msg.getMessageTemplate(compiler.longMessages);
        ST locationST = getLocationFormat();
        ST reportST = getReportFormat(msg.getErrorType().severity);
        ST messageFormatST = getMessageFormat();

        boolean locationValid = false;
        if (msg.line != -1) {
            locationST.add("line", msg.line);
            locationValid = true;
        }
        if (msg.charPosition != -1) {
            locationST.add("column", msg.charPosition);
            locationValid = true;
        }
        if (msg.fileName != null) {
            File f = new File(msg.fileName);
            // Don't show path to file in messages; too long.
            String displayFileName = msg.fileName;
            if ( f.exists() ) {
                displayFileName = f.getName();
            }
            locationST.add("file", displayFileName);
            locationValid = true;
        }
        messageFormatST.add("id", msg.getErrorType().code);
        messageFormatST.add("text", messageST);

        if (locationValid) reportST.add("location", locationST);
        reportST.add("message", messageFormatST);

        return reportST;
    }

    public ST getMessageFormat() {
        return format.getInstanceOf("message");
    }

    public ST getLocationFormat() {
        return format.getInstanceOf("location");
    }

    public ST getReportFormat(ErrorSeverity severity) {
        ST st = format.getInstanceOf("report");
        st.add("type", severity.getText());
        return st;
    }

    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                            int line, int charPositionInLine, String msg,
                            RecognitionException e) {
        CarbonMessage m =
                new LanguageSyntaxMessage(ErrorKind.SYNTAX_ERROR,
                        (Token) offendingSymbol, e, msg);
        emit(ErrorKind.SYNTAX_ERROR, m);
    }

    public void semanticError(ErrorKind kind, String fileName, Token token,
                              Object ... args) {
        CarbonMessage m =
                new LanguageSemanticsMessage(kind, fileName, token, args);
        emit(kind, m);
    }

    public void toolError(ErrorKind kind, String msg) {

    }

    public boolean formatWantsSingleLineMessage() {
        return format.getInstanceOf("wantsSingleLineMessage")
                .render().equals("true");
    }

    @SuppressWarnings("fallthrough")
    public void emit(ErrorKind kind, CarbonMessage msg) {
        switch (kind.severity) {
            case WARNING_ONE_OFF:
                if (errorTypes.contains(kind)) {
                    break;
                }
                // fall thru
            case WARNING:
                warningCount++;
                compiler.warning(msg);
                break;
            case ERROR_ONE_OFF:
                if (errorTypes.contains(kind)) {
                    break;
                }
                // fall thru
            case ERROR:
                errorCount++;
                compiler.error(msg);
                break;
        }
        errorTypes.add(kind);
    }
}
