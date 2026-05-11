package org.lab5.managers;

import org.lab5.exceptions.ValidationException;
import org.lab5.models.Address;
import org.lab5.models.Organization;
import org.lab5.models.OrganizationType;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;

public class CollectionManager {
    private final PriorityQueue<Organization> queue = new PriorityQueue<>();
    private final ZonedDateTime collectionInitializationTime = ZonedDateTime.now();

    public synchronized void replaceAll(List<Organization> items) throws ValidationException {
        Set<Integer> ids = new HashSet<>();
        for (Organization organization : items) {
            if (!ids.add(organization.getId())) {
                throw new ValidationException("duplicate id in data file: " + organization.getId());
            }
            organization.validate();
        }

        queue.clear();
        queue.addAll(items);
    }

    public synchronized Organization addNew(Organization.Draft draft, IdGenerator idGenerator, ZonedDateTime creationMoment)
            throws ValidationException {
        draft.validate();

        int id = idGenerator.nextId();
        Organization organization = draft.toOrganization(id, creationMoment);
        organization.validate();
        queue.add(organization);

        return organization;
    }

    public synchronized Organization update(int id, Organization.Draft draft) throws ValidationException {
        draft.validate();

        Organization existing = findById(id);
        if (existing == null) {
            throw new ValidationException("organization with id=" + id + " not found");
        }

        removeById(id);

        Organization updated = existing.copyPreservingIdAndCreation(
                draft.getName(),
                draft.getCoordinates(),
                draft.getAnnualTurnover(),
                draft.getEmployeesCount(),
                draft.getType(),
                draft.getPostalAddress(),
                existing.getOwnerId(),
                existing.getOwnerUsername()
        );

        updated.validate();
        queue.add(updated);
        return updated;
    }

    public synchronized boolean removeById(int id) {
        Organization target = findById(id);
        if (target == null) {
            return false;
        }

        queue.remove(target);
        return true;
    }

    public synchronized void clear() {
        queue.clear();
    }

    public synchronized Organization removeHead() {
        return queue.poll();
    }

    public synchronized Organization addIfMin(Organization.Draft draft, IdGenerator idGenerator, ZonedDateTime creationMoment)
            throws ValidationException {
        draft.validate();

        if (queue.isEmpty() || isDraftLessThanMin(draft)) {
            return addNew(draft, idGenerator, creationMoment);
        }

        return null;
    }

    private synchronized boolean isDraftLessThanMin(Organization.Draft draft) {
        Organization min = queue.peek();

        int result = Double.compare(draft.getAnnualTurnover(), min.getAnnualTurnover());
        if (result != 0) {
            return result < 0;
        }

        result = Long.compare(draft.getEmployeesCount(), min.getEmployeesCount());
        if (result != 0) {
            return result < 0;
        }

        return draft.getName().compareTo(min.getName()) < 0;
    }

    public synchronized int removeLower(Organization.Draft thresholdDraft) throws ValidationException {
        thresholdDraft.validate();

        Organization threshold = thresholdDraft.toOrganization(Integer.MAX_VALUE, ZonedDateTime.now());

        List<Organization> toKeep = new ArrayList<>();
        int removed = 0;

        for (Organization organization : queue) {
            if (organization.compareTo(threshold) >= 0) {
                toKeep.add(organization);
            } else {
                removed++;
            }
        }

        queue.clear();
        queue.addAll(toKeep);

        return removed;
    }

    public synchronized int removeAllByPostalAddress(Address address) {
        List<Organization> toKeep = new ArrayList<>();
        int removed = 0;

        for (Organization organization : queue) {
            if (Objects.equals(organization.getPostalAddress(), address)) {
                removed++;
            } else {
                toKeep.add(organization);
            }
        }

        queue.clear();
        queue.addAll(toKeep);

        return removed;
    }

    public synchronized int countGreaterThanType(OrganizationType reference) {
        int count = 0;

        for (Organization organization : queue) {
            OrganizationType type = organization.getType();
            if (reference == null && type != null) {
                count++;
            } else if (reference != null && type != null && type.ordinal() > reference.ordinal()) {
                count++;
            }
        }

        return count;
    }

    public synchronized List<Organization> snapshotAll() {
        return new ArrayList<>(queue);
    }

    public synchronized List<Organization> getSortedView() {
        PriorityQueue<Organization> copy = new PriorityQueue<>(queue);
        List<Organization> result = new ArrayList<>();

        while (!copy.isEmpty()) {
            result.add(copy.poll());
        }

        return result;
    }

    public synchronized int size() {
        return queue.size();
    }

    public ZonedDateTime getInitializationTime() {
        return collectionInitializationTime;
    }

    public String getCollectionTypeName() {
        return queue.getClass().getName();
    }

    public synchronized Organization findById(int id) {
        for (Organization organization : queue) {
            if (organization.getId() == id) {
                return organization;
            }
        }

        return null;
    }

    public static Comparator<Address> postalAddressDescendingNullsLast() {
        Comparator<Address> comparator = Comparator.comparing(
                Address::getStreet,
                Comparator.nullsFirst(String::compareTo)
        ).thenComparing(
                Address::getZipCode,
                Comparator.nullsFirst(String::compareTo)
        );

        return Comparator.nullsLast(comparator.reversed());
    }

    public synchronized void addExisting(Organization organization) {
        queue.add(organization);
    }

    public synchronized int removeByOwner(int ownerId) {
        List<Organization> toKeep = new ArrayList<>();
        int removed = 0;
        for (Organization organization : queue) {
            if (organization.getOwnerId() != null && organization.getOwnerId() == ownerId) {
                removed++;
            } else {
                toKeep.add(organization);
            }
        }
        queue.clear();
        queue.addAll(toKeep);
        return removed;
    }

    public synchronized int removeLowerByOwner(Organization threshold, int ownerId) {
        List<Organization> toKeep = new ArrayList<>();
        int removed = 0;
        for (Organization organization : queue) {
            boolean ownerMatches = organization.getOwnerId() != null && organization.getOwnerId() == ownerId;
            if (ownerMatches && organization.compareTo(threshold) < 0) {
                removed++;
            } else {
                toKeep.add(organization);
            }
        }
        queue.clear();
        queue.addAll(toKeep);
        return removed;
    }

    public synchronized int removeAllByPostalAddressAndOwner(Address address, int ownerId) {
        List<Organization> toKeep = new ArrayList<>();
        int removed = 0;
        for (Organization organization : queue) {
            boolean ownerMatches = organization.getOwnerId() != null && organization.getOwnerId() == ownerId;
            if (ownerMatches && Objects.equals(organization.getPostalAddress(), address)) {
                removed++;
            } else {
                toKeep.add(organization);
            }
        }
        queue.clear();
        queue.addAll(toKeep);
        return removed;
    }
}
