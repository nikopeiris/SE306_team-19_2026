package business.validation;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Set;

import business.lavsexceptions.LavsException;
import business.system.Customer;
import business.system.customer.CustomerAddressService;
import business.system.customer.CustomerEmailService;
import business.system.customer.CustomerEmployerService;
import business.system.customer.CustomerPhoneNumberService;
import dataaccess.systemadapters.MainframeAdapter;
import mainframe.client.messages.loan.Create;
import mainframe.client.messages.loan.UpdateStatus;

/**
 * Validation utilities for loans.
 */
public final class LoanValidation {
    public static final Set<String> VALID_LOAN_TYPES = Set.of(
            Create.RATETYPE_FIXED,
            Create.RATETYPE_FLOATING,
            Create.RATETYPE_INTERESTONLY
    );

    public static final Set<String> VALID_PAYMENT_FREQUENCIES = Set.of(
            Create.PAYMENTFREQUENCY_WEEKLY,
            Create.PAYMENTFREQUENCY_FORTNIGHTLY,
            Create.PAYMENTFREQUENCY_MONTHLY,
            Create.PAYMENTFREQUENCY_YEARLY,
            Create.PAYMENTFREQUENCY_ONRENEWAL
    );

    public static final Set<String> VALID_COMPOUND_TYPES = Set.of(
            Create.RATEFREQUENCY_DAILY,
            Create.RATEFREQUENCY_WEEKLY,
            Create.RATEFREQUENCY_FORTNIGHTLY,
            Create.RATEFREQUENCY_MONTHLY,
            Create.RATEFREQUENCY_YEARLY
    );

    private LoanValidation() {}

    public static void validateLoanDetails(
            String customerNumber,
            double principal,
            double annualRate,
            int term,
            String loanType,
            String paymentFrequency,
            String compoundType,
            String renewalDate,
            String startDate)
            throws LavsException, IOException {
        ValidationResult result = new ValidationResult()
                .requireNotBlank("customerNumber", customerNumber,
                        "Customer number cannot be null or empty")
                .requireNotBlank("loanType", loanType,
                        "Loan type cannot be null or empty")
                .requireNotBlank("paymentFrequency", paymentFrequency,
                        "Payment frequency cannot be null or empty")
                .requireNotBlank("compoundType", compoundType,
                        "Compound type cannot be null or empty")
                .requireNotBlank("renewalDate", renewalDate,
                        "Renewal date cannot be null or empty")
                .requireNotBlank("startDate", startDate,
                        "Start date cannot be null or empty");

        if (principal <= 0) {
            result.addError("principal", "Loan principal must be greater than zero");
        }
        if (annualRate < 0) {
            result.addError("annualRate", "Annual rate cannot be negative");
        }
        if (term <= 0) {
            result.addError("term", "Term must be greater than zero");
        }

        if (loanType != null && !loanType.isBlank() && !VALID_LOAN_TYPES.contains(loanType.trim())) {
            result.addError("loanType", "Loan type must be a valid category code");
        }
        if (paymentFrequency != null && !paymentFrequency.isBlank()
                && !VALID_PAYMENT_FREQUENCIES.contains(paymentFrequency.trim())) {
            result.addError("paymentFrequency", "Payment frequency must be a valid category code");
        }
        if (compoundType != null && !compoundType.isBlank()
                && !VALID_COMPOUND_TYPES.contains(compoundType.trim())) {
            result.addError("compoundType", "Compound type must be a valid category code");
        }

        validateDate(result, startDate, renewalDate);

        if (!result.isValid()) {
            throw new ValidationException(result);
        }
    }

    public static void validateUpdateLoanDetails(
            String customerNumber,
            String loanNumber,
            double paymentAmount,
            String paymentFrequency,
            double principal,
            double rateAmount,
            String rateFrequency,
            String rateType,
            String renewalDate,
            String startDate) throws ValidationException {
        validateUpdateLoanDetails(customerNumber, loanNumber, paymentAmount, paymentFrequency,
                principal, rateAmount, rateFrequency, rateType, renewalDate, startDate, null);
    }

    /**
     * Same checks as above, except a start date equal to {@code originalStartDate} (the
     * loan's existing start date) may be in the past, since it is not being changed.
     */
    public static void validateUpdateLoanDetails(
            String customerNumber,
            String loanNumber,
            double paymentAmount,
            String paymentFrequency,
            double principal,
            double rateAmount,
            String rateFrequency,
            String rateType,
            String renewalDate,
            String startDate,
            String originalStartDate) throws ValidationException {
        ValidationResult result = new ValidationResult()
                .requireNotBlank("customerNumber", customerNumber,
                        "Customer number cannot be null or empty")
                .requireNotBlank("loanNumber", loanNumber,
                        "Loan number cannot be null or empty")
                .requireNotBlank("paymentFrequency", paymentFrequency,
                        "Payment frequency cannot be null or empty")
                .requireNotBlank("rateFrequency", rateFrequency,
                        "Rate frequency cannot be null or empty")
                .requireNotBlank("rateType", rateType,
                        "Rate type cannot be null or empty")
                .requireNotBlank("renewalDate", renewalDate,
                        "Renewal date cannot be null or empty")
                .requireNotBlank("startDate", startDate,
                        "Start date cannot be null or empty");

        if (paymentAmount <= 0) {
            result.addError("paymentAmount", "Payment amount must be greater than zero");
        }
        if (principal <= 0) {
            result.addError("principal", "Loan principal must be greater than zero");
        }
        if (rateAmount < 0) {
            result.addError("rateAmount", "Rate amount cannot be negative");
        }

        if (rateType != null && !rateType.isBlank() && !VALID_LOAN_TYPES.contains(rateType.trim())) {
            result.addError("rateType", "Rate type must be a valid category code");
        }
        if (paymentFrequency != null && !paymentFrequency.isBlank()
                && !VALID_PAYMENT_FREQUENCIES.contains(paymentFrequency.trim())) {
            result.addError("paymentFrequency", "Payment frequency must be a valid category code");
        }
        if (rateFrequency != null && !rateFrequency.isBlank()
                && !VALID_COMPOUND_TYPES.contains(rateFrequency.trim())) {
            result.addError("rateFrequency", "Rate frequency must be a valid category code");
        }

        boolean startUnchanged = startDate != null && originalStartDate != null
                && startDate.trim().equals(originalStartDate.trim());
        validateDate(result, startUnchanged ? null : startDate, renewalDate);

        if (!result.isValid()) {
            throw new ValidationException(result);
        }
    }

    public static void validateUpdateLoanStatus(String loanNumber, String status)
            throws ValidationException {
        ValidationResult result = new ValidationResult()
                .requireNotBlank("loanNumber", loanNumber,
                        "Loan number cannot be null or empty")
                .requireNotBlank("status", status,
                        "Status cannot be null or empty");

        if (status != null && !status.isBlank()
                && !Set.of(
                        UpdateStatus.STATUS_NEW,
                        UpdateStatus.STATUS_PENDING,
                        UpdateStatus.STATUS_ACTIVE,
                        UpdateStatus.STATUS_DEFAULTED,
                        UpdateStatus.STATUS_CANCELLED)
                        .contains(status.trim())) {
            result.addError("status", "Status must be a valid loan status");
        }

        result.throwIfInvalid();
    }

    public static void validateDate(ValidationResult result, String startDate, String renewalDate) throws ValidationException {
        if (renewalDate != null && !renewalDate.isBlank()) {
            try {
                LocalDate renewalLocalDate = LocalDate.parse(renewalDate.trim());
                if (renewalLocalDate.isBefore(LocalDate.now())) {
                    result.addError("renewalDate", "Renewal date must be today or later");
                }
            } catch (DateTimeParseException e) {
                result.addError("renewalDate", "Renewal date must be in yyyy-MM-dd format");
            }
        }

        if (startDate != null && !startDate.isBlank()) {
            try {
                LocalDate startLocalDate = LocalDate.parse(startDate.trim());
                if (startLocalDate.isBefore(LocalDate.now())) {
                    result.addError("startDate", "Start date must be today or later");
                }
            } catch (DateTimeParseException e) {
                result.addError("startDate", "Start date must be in yyyy-MM-dd format");
            }
        }
        if (!result.isValid()) {
            throw new ValidationException(result);
        }
    }

    public static void preReqCheck(String customerNumber, MainframeAdapter adapter) throws IOException, LavsException {
        if (customerNumber != null && !customerNumber.isBlank()) {
            ValidationResult result = new ValidationResult();
            CustomerAddressService addressService = new CustomerAddressService(adapter);
            CustomerEmailService emailService = new CustomerEmailService(adapter);
            CustomerPhoneNumberService phoneService = new CustomerPhoneNumberService(adapter);
            if (addressService.findMailingAddress(customerNumber.trim()) == null) {
                result.addError("mailingAddress", "Does not have a mailing address.");
            }
            if ("".equals(emailService.findPrimaryEmail(customerNumber.trim()))) {
                result.addError("email", "Does not have a primary email.");
            } else {
                if (!result.isValid()) {
                    throw new ValidationException(result);
                }
                return;
            }
            if (phoneService.listPhoneNumbers(customerNumber.trim(), 1L, 100L).length == 0) {
                result.addError("phone", "Does not have a primary phone number.");
            } else {
                result.removeError("email");
            }
            if (!result.isValid()) {
                throw new ValidationException(result);
            }
        }
    }

    public static void validateCustomerJobAndGuarantor(String customerId, String guarantorId, MainframeAdapter adapter)
            throws IOException, LavsException {
        ValidationResult result = new ValidationResult();

        CustomerEmployerService employerService = new CustomerEmployerService(adapter);
        mainframe.client.messages.customerEmployer.List.Item[] employers =
                employerService.listEmployers(customerId, 1L, 100L);

        boolean hasJobs = employers != null && employers.length > 0;

        if (customerId.equals(guarantorId)) {
            result.addError("guarantorId", "Guarantor cannot be the same as the customer.");
            throw new ValidationException(result);
        }

        if (!hasJobs) {
            if (guarantorId == null || guarantorId.isBlank()) {
                result.addError("guarantorId", "Guarantor is required when customer has no employment.");
                throw new ValidationException(result);
            }
        }

        if (guarantorId != null && !guarantorId.isBlank()) {
            Customer customerService = new Customer(adapter);
            try {
                customerService.retrieveCustomer(guarantorId.trim());
            } catch (LavsException e) {
                result.addError("guarantorId", "Guarantor must be a valid customer.");
                throw new ValidationException(result);
            }
        }

        if (!result.isValid()) {
            throw new ValidationException(result);
        }
    }
}