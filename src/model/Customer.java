package model;

public record Customer(long id, String firstName, String lastName, String address, String phone, boolean employed) {
    public Customer {
        firstName = clean(firstName);
        lastName = clean(lastName);
        address = clean(address);
        phone = clean(phone);
        if (firstName.isBlank() || lastName.isBlank() || address.isBlank() || phone.isBlank()) {
            throw new IllegalArgumentException("Name, address, and phone are required.");
        }
    }

    private static String clean(String value) { return value == null ? "" : value.strip(); }
    public String fullName() { return firstName + " " + lastName; }
    @Override public String toString() { return fullName()+" · CUS-"+id; }
}
