// ResponseStatus.java
package eu.frigo.dispensa.network.tosano.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class ResponseStatus {

    @SerializedName("status")
    private int status;

    @SerializedName("errors")
    private List<Object> errors; // Sostituisci Object con una classe specifica se nota

    @SerializedName("infos")
    private List<Object> infos; // Sostituisci Object con una classe specifica se nota

    @SerializedName("warnings")
    private List<Object> warnings; // Sostituisci Object con una classe specifica se nota

    @SerializedName("errorsMessage")
    private List<Object> errorsMessage;

    @SerializedName("infosMessage")
    private List<Object> infosMessage;

    @SerializedName("warningsMessage")
    private List<Object> warningsMessage;

    // Getters e Setters
    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public List<Object> getErrors() {
        return errors;
    }

    public void setErrors(List<Object> errors) {
        this.errors = errors;
    }

    public List<Object> getInfos() {
        return infos;
    }

    public void setInfos(List<Object> infos) {
        this.infos = infos;
    }

    public List<Object> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<Object> warnings) {
        this.warnings = warnings;
    }

    public List<Object> getErrorsMessage() {
        return errorsMessage;
    }

    public void setErrorsMessage(List<Object> errorsMessage) {
        this.errorsMessage = errorsMessage;
    }

    public List<Object> getInfosMessage() {
        return infosMessage;
    }

    public void setInfosMessage(List<Object> infosMessage) {
        this.infosMessage = infosMessage;
    }

    public List<Object> getWarningsMessage() {
        return warningsMessage;
    }

    public void setWarningsMessage(List<Object> warningsMessage) {
        this.warningsMessage = warningsMessage;
    }

    @Override
    public String toString() {
        return "ResponseStatus{" +
                "status=" + status +
                ", errors=" + errors +
                ", infos=" + infos +
                ", warnings=" + warnings +
                ", errorsMessage=" + errorsMessage +
                ", infosMessage=" + infosMessage +
                ", warningsMessage=" + warningsMessage +
                '}';
    }
}
