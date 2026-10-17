package business.system;

import java.io.IOException;
import java.util.Set;

import business.Main;
import business.lavsexceptions.LavsException;
import business.validation.ValidationException;
import business.validation.ValidationResult;
import dataaccess.systemadapters.MainframeAdapter;
import mainframe.client.Status;
import mainframe.client.messages.loan.AddCoborrower;
import mainframe.client.messages.loan.DeleteCoborrower;

public class Coborrower extends Customer {
    private static final Set<String> VALID_COBORROWER_TYPES = Set.of(
            AddCoborrower.TYPE_COBORROWER,
            AddCoborrower.TYPE_GUARANTOR
    );

    private final MainframeAdapter coborrowerAdapter;

    public Coborrower(MainframeAdapter adapter) {
        super(adapter);
        this.coborrowerAdapter = adapter;
    }

    /**
     * Adds a co-borrower or guarantor to a loan after validating the input details.
     *
     * @param coborrowerNumber customer identifier for the co-borrower
     * @param loanNumber loan identifier to attach the co-borrower to
     * @param type co-borrower type ("Coborrower" or "Guarantor")
     * @return the created AddCoborrower mainframe message
     * @throws ValidationException if business validation fails
     * @throws LavsException if the mainframe rejects the request
     * @throws IOException if the mainframe cannot be reached
     */
    public AddCoborrower addCoborrower(String coborrowerNumber, String loanNumber, String type)
            throws IOException, LavsException {
        validateCoborrowerDetails(coborrowerNumber, loanNumber, type);

        AddCoborrower addCoborrowerMessage = new AddCoborrower(
                coborrowerNumber.trim(),
                loanNumber.trim(),
                type.trim());

        Status status = coborrowerAdapter.send(addCoborrowerMessage);
        Main.isValidStatus(status);
        return addCoborrowerMessage;
    }

    /**
     * Deletes a co-borrower from a loan after validating the input details.
     *
     * @param id the unique id of the co-borrower relationship
     * @param loanNumber loan identifier
     * @return the DeleteCoborrower mainframe message
     * @throws ValidationException if business validation fails
     * @throws LavsException if the mainframe rejects the request
     * @throws IOException if the mainframe cannot be reached
     */
    public DeleteCoborrower deleteCoborrower(Long id, String loanNumber)
            throws IOException, LavsException {
        validateDeleteCoborrowerDetails(id, loanNumber);

        DeleteCoborrower deleteCoborrowerMessage = new DeleteCoborrower(id, loanNumber.trim());
        Status status = coborrowerAdapter.send(deleteCoborrowerMessage);
        Main.isValidStatus(status);
        return deleteCoborrowerMessage;
    }

    public void validateCoborrowerDetails(String coborrowerNumber, String loanNumber, String type)
            throws ValidationException {
        ValidationResult result = new ValidationResult()
                .requireNotBlank("coborrowerNumber", coborrowerNumber,
                        "Coborrower number cannot be null or empty")
                .requireNotBlank("loanNumber", loanNumber,
                        "Loan number cannot be null or empty")
                .requireNotBlank("type", type,
                        "Coborrower type cannot be null or empty");

        if (type != null && !type.isBlank() && !VALID_COBORROWER_TYPES.contains(type.trim())) {
            result.addError("type", "Coborrower type is invalid");
        }

        if (!result.isValid()) {
            throw new ValidationException(result);
        }
    }

    public void validateDeleteCoborrowerDetails(Long id, String loanNumber)
            throws ValidationException {
        ValidationResult result = new ValidationResult()
                .requireNotBlank("loanNumber", loanNumber,
                        "Loan number cannot be null or empty");

        if (id == null) {
            result.addError("id", "Coborrower id cannot be empty");
        } else if (id < 1) {
            result.addError("id", "Coborrower id must be a positive integer");
        }

        if (!result.isValid()) {
            throw new ValidationException(result);
        }
    }
}
