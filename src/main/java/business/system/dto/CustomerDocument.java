package business.system.dto;

/**
 * One document on a customer's record, as plain data for the UI.
 * The mainframe's document list only returns the id, type and file name.
 */
public record CustomerDocument(
        Long id,
        String type,
        String fileName) {

    public CustomerDocument {
        type = clean(type);
        fileName = clean(fileName);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}