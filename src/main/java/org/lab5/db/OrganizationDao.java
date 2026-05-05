package org.lab5.db;

import org.lab5.models.Address;
import org.lab5.models.Coordinates;
import org.lab5.models.Organization;
import org.lab5.models.OrganizationType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public class OrganizationDao {
    private final DatabaseManager databaseManager;

    public OrganizationDao(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public List<Organization> findAll() throws SQLException {
        String sql = """
                SELECT o.id, o.name, o.coordinates_x, o.coordinates_y, o.creation_date,
                       o.annual_turnover, o.employees_count, o.type, o.street, o.zip_code,
                       o.owner_id, u.username
                FROM organizations o
                JOIN users u ON u.id = o.owner_id
                """;
        List<Organization> organizations = new ArrayList<>();
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                organizations.add(map(resultSet));
            }
        }
        return organizations;
    }

    public Organization insert(Organization.Draft draft, int ownerId, String ownerUsername) throws SQLException {
        String sql = """
                INSERT INTO organizations(
                    name, coordinates_x, coordinates_y, creation_date, annual_turnover,
                    employees_count, type, street, zip_code, owner_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id, creation_date
                """;
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, draft.getName());
            statement.setInt(2, draft.getCoordinates().getX());
            statement.setFloat(3, draft.getCoordinates().getY());
            statement.setTimestamp(4, Timestamp.from(java.time.Instant.now()));
            statement.setDouble(5, draft.getAnnualTurnover());
            statement.setLong(6, draft.getEmployeesCount());
            statement.setString(7, draft.getType() == null ? null : draft.getType().name());
            statement.setString(8, draft.getPostalAddress() == null ? null : draft.getPostalAddress().getStreet());
            statement.setString(9, draft.getPostalAddress() == null ? null : draft.getPostalAddress().getZipCode());
            statement.setInt(10, ownerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                int id = resultSet.getInt("id");
                java.time.ZonedDateTime creationDate = resultSet.getTimestamp("creation_date")
                        .toInstant()
                        .atZone(ZoneId.systemDefault());
                return new Organization(
                        id,
                        draft.getName(),
                        draft.getCoordinates(),
                        creationDate,
                        draft.getAnnualTurnover(),
                        draft.getEmployeesCount(),
                        draft.getType(),
                        draft.getPostalAddress(),
                        ownerId,
                        ownerUsername
                );
            }
        }
    }

    public boolean update(int id, Organization.Draft draft, int ownerId, String ownerUsername) throws SQLException {
        String sql = """
                UPDATE organizations
                SET name = ?, coordinates_x = ?, coordinates_y = ?, annual_turnover = ?,
                    employees_count = ?, type = ?, street = ?, zip_code = ?
                WHERE id = ? AND owner_id = ?
                """;
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, draft.getName());
            statement.setInt(2, draft.getCoordinates().getX());
            statement.setFloat(3, draft.getCoordinates().getY());
            statement.setDouble(4, draft.getAnnualTurnover());
            statement.setLong(5, draft.getEmployeesCount());
            statement.setString(6, draft.getType() == null ? null : draft.getType().name());
            statement.setString(7, draft.getPostalAddress() == null ? null : draft.getPostalAddress().getStreet());
            statement.setString(8, draft.getPostalAddress() == null ? null : draft.getPostalAddress().getZipCode());
            statement.setInt(9, id);
            statement.setInt(10, ownerId);
            return statement.executeUpdate() > 0;
        }
    }

    public boolean deleteById(int id, int ownerId) throws SQLException {
        String sql = "DELETE FROM organizations WHERE id = ? AND owner_id = ?";
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            statement.setInt(2, ownerId);
            return statement.executeUpdate() > 0;
        }
    }

    public int deleteAllByOwner(int ownerId) throws SQLException {
        String sql = "DELETE FROM organizations WHERE owner_id = ?";
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, ownerId);
            return statement.executeUpdate();
        }
    }

    public int deleteLowerThan(Organization threshold, int ownerId) throws SQLException {
        String sql = """
                DELETE FROM organizations
                WHERE owner_id = ?
                  AND (
                        annual_turnover < ?
                        OR (annual_turnover = ? AND employees_count < ?)
                        OR (annual_turnover = ? AND employees_count = ? AND name < ?)
                      )
                """;
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, ownerId);
            statement.setDouble(2, threshold.getAnnualTurnover());
            statement.setDouble(3, threshold.getAnnualTurnover());
            statement.setLong(4, threshold.getEmployeesCount());
            statement.setDouble(5, threshold.getAnnualTurnover());
            statement.setLong(6, threshold.getEmployeesCount());
            statement.setString(7, threshold.getName());
            return statement.executeUpdate();
        }
    }

    public int deleteAllByPostalAddress(Address address, int ownerId) throws SQLException {
        String sql = """
                DELETE FROM organizations
                WHERE owner_id = ?
                  AND street IS NOT DISTINCT FROM ?
                  AND zip_code IS NOT DISTINCT FROM ?
                """;
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, ownerId);
            statement.setString(2, address == null ? null : address.getStreet());
            statement.setString(3, address == null ? null : address.getZipCode());
            return statement.executeUpdate();
        }
    }

    private Organization map(ResultSet resultSet) throws SQLException {
        String typeValue = resultSet.getString("type");
        OrganizationType type = typeValue == null ? null : OrganizationType.valueOf(typeValue);
        String street = resultSet.getString("street");
        String zipCode = resultSet.getString("zip_code");
        Address address = (street == null && zipCode == null) ? null : new Address(street, zipCode);
        return new Organization(
                resultSet.getInt("id"),
                resultSet.getString("name"),
                new Coordinates(resultSet.getInt("coordinates_x"), resultSet.getFloat("coordinates_y")),
                resultSet.getTimestamp("creation_date").toInstant().atZone(ZoneId.systemDefault()),
                resultSet.getDouble("annual_turnover"),
                resultSet.getLong("employees_count"),
                type,
                address,
                resultSet.getInt("owner_id"),
                resultSet.getString("username")
        );
    }
}
