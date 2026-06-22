package charg.ing.stations.entity.enums;

public enum UserRole {
    USER,
    CONTRACTOR,
    SPECIALIST,
    ADMIN;

    public static UserRole fromString(String role) {
        try {
            return UserRole.valueOf(role.toUpperCase());
        } catch (IllegalArgumentException e) {
            return USER;
        }
    }

    public String getRoleWithPrefix() {
        return "ROLE_" + this.name();
    }
}