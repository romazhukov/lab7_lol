package org.lab5.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.lab5.exceptions.ValidationException;
import org.lab5.util.Validators;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Objects;

public class Organization implements Comparable<Organization>, Serializable {
    private static final long serialVersionUID = 1L;

    private final int id;
    private final String name;
    private final Coordinates coordinates;
    private final ZonedDateTime creationDate;
    private final double annualTurnover;
    private final long employeesCount;
    private final OrganizationType type;
    private final Address postalAddress;
    private final Integer ownerId;
    private final String ownerUsername;

    @JsonCreator
    public Organization(
            @JsonProperty("id") int id,
            @JsonProperty("name") String name,
            @JsonProperty("coordinates") Coordinates coordinates,
            @JsonProperty("creationDate") ZonedDateTime creationDate,
            @JsonProperty("annualTurnover") double annualTurnover,
            @JsonProperty("employeesCount") long employeesCount,
            @JsonProperty("type") OrganizationType type,
            @JsonProperty("postalAddress") Address postalAddress,
            @JsonProperty("ownerId") Integer ownerId,
            @JsonProperty("ownerUsername") String ownerUsername
    ) {
        this.id = id;
        this.name = name;
        this.coordinates = coordinates;
        this.creationDate = creationDate;
        this.annualTurnover = annualTurnover;
        this.employeesCount = employeesCount;
        this.type = type;
        this.postalAddress = postalAddress;
        this.ownerId = ownerId;
        this.ownerUsername = ownerUsername;
    }

    public void validate() throws ValidationException {
        if (id <= 0) {
            throw new ValidationException("id must be > 0");
        }
        if (name == null || name.isBlank()) {
            throw new ValidationException("name must not be empty");
        }
        if (coordinates == null) {
            throw new ValidationException("coordinates must not be null");
        }
        if (coordinates.getX() == null) {
            throw new ValidationException("coordinates.x must not be null");
        }
        if (creationDate == null) {
            throw new ValidationException("creationDate must not be null");
        }
        if (annualTurnover <= 0) {
            throw new ValidationException("annualTurnover must be > 0");
        }
        if (employeesCount <= 0) {
            throw new ValidationException("employeesCount must be > 0");
        }
        if (postalAddress != null) {
            if (postalAddress.getStreet() != null && postalAddress.getStreet().length() > 190) {
                throw new ValidationException("street length must be <= 190");
            }
            if (postalAddress.getZipCode() != null && postalAddress.getZipCode().length() > 30) {
                throw new ValidationException("zipCode length must be <= 30");
            }
        }
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Coordinates getCoordinates() {
        return coordinates;
    }

    public ZonedDateTime getCreationDate() {
        return creationDate;
    }

    public double getAnnualTurnover() {
        return annualTurnover;
    }

    public long getEmployeesCount() {
        return employeesCount;
    }

    public OrganizationType getType() {
        return type;
    }

    public Address getPostalAddress() {
        return postalAddress;
    }

    public Integer getOwnerId() {
        return ownerId;
    }

    public String getOwnerUsername() {
        return ownerUsername;
    }

    public Organization copyPreservingIdAndCreation(
            String name,
            Coordinates coordinates,
            double annualTurnover,
            long employeesCount,
            OrganizationType type,
            Address postalAddress,
            Integer ownerId,
            String ownerUsername
    ) {
        return new Organization(
                this.id,
                name,
                coordinates,
                this.creationDate,
                annualTurnover,
                employeesCount,
                type,
                postalAddress,
                ownerId,
                ownerUsername
        );
    }

    @Override
    public int compareTo(Organization other) {
        int result = Double.compare(this.annualTurnover, other.annualTurnover);
        if (result != 0) {
            return result;
        }

        result = Long.compare(this.employeesCount, other.employeesCount);
        if (result != 0) {
            return result;
        }

        result = this.name.compareTo(other.name);
        if (result != 0) {
            return result;
        }

        return Integer.compare(this.id, other.id);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Organization that = (Organization) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Organization{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", coordinates=" + coordinates +
                ", creationDate=" + creationDate +
                ", annualTurnover=" + annualTurnover +
                ", employeesCount=" + employeesCount +
                ", type=" + type +
                ", postalAddress=" + postalAddress +
                ", ownerUsername='" + ownerUsername + '\'' +
                '}';
    }

    public static class Draft implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String name;
        private final Coordinates coordinates;
        private final double annualTurnover;
        private final long employeesCount;
        private final OrganizationType type;
        private final Address postalAddress;

        public Draft(
                String name,
                Coordinates coordinates,
                double annualTurnover,
                long employeesCount,
                OrganizationType type,
                Address postalAddress
        ) {
            this.name = name;
            this.coordinates = coordinates;
            this.annualTurnover = annualTurnover;
            this.employeesCount = employeesCount;
            this.type = type;
            this.postalAddress = postalAddress;
        }

        public String getName() {
            return name;
        }

        public Coordinates getCoordinates() {
            return coordinates;
        }

        public double getAnnualTurnover() {
            return annualTurnover;
        }

        public long getEmployeesCount() {
            return employeesCount;
        }

        public OrganizationType getType() {
            return type;
        }

        public Address getPostalAddress() {
            return postalAddress;
        }

        public void validate() throws ValidationException {
            Validators.requireNonBlankName(name);
            Validators.validateCoordinates(coordinates);
            Validators.requirePositiveTurnover(annualTurnover);
            Validators.requirePositiveEmployees(employeesCount);

            if (postalAddress != null) {
                Validators.validateStreet(postalAddress.getStreet());
                Validators.validateZip(postalAddress.getZipCode());
            }
        }

        public Organization toOrganization(int id, ZonedDateTime creationDate) {
            return new Organization(
                    id,
                    name,
                    coordinates,
                    creationDate,
                    annualTurnover,
                    employeesCount,
                    type,
                    postalAddress,
                    null,
                    null
            );
        }
    }
}