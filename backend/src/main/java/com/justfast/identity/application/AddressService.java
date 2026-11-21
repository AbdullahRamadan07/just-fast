package com.justfast.identity.application;

import com.justfast.common.config.StoreProperties;
import com.justfast.common.error.BusinessRuleException;
import com.justfast.common.error.NotFoundException;
import com.justfast.identity.domain.Address;
import com.justfast.identity.domain.Address.AddressDetails;
import com.justfast.identity.infrastructure.AddressRepository;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AddressService {

    private final AddressRepository addresses;
    private final StoreProperties store;
    private final Clock clock;

    public AddressService(AddressRepository addresses, StoreProperties store, Clock clock) {
        this.addresses = addresses;
        this.store = store;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Address> list(Long userId) {
        return addresses.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /** Returns the address only if it belongs to the user; otherwise behaves as if it does not exist. */
    @Transactional(readOnly = true)
    public Address requireOwned(Long userId, Long addressId) {
        return addresses.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new NotFoundException("Address not found."));
    }

    @Transactional
    public Address create(Long userId, AddressDetails details) {
        requireShippableCountry(details.country());
        return addresses.save(new Address(userId, details, clock.instant()));
    }

    @Transactional
    public Address update(Long userId, Long addressId, AddressDetails details) {
        requireShippableCountry(details.country());
        Address address = requireOwned(userId, addressId);
        address.apply(details);
        return address;
    }

    @Transactional
    public void delete(Long userId, Long addressId) {
        addresses.delete(requireOwned(userId, addressId));
    }

    public void requireShippableCountry(String country) {
        if (country == null || !store.shippingCountry().equalsIgnoreCase(country.trim())) {
            throw new BusinessRuleException("UNSUPPORTED_SHIPPING_REGION",
                    "We currently ship only to " + store.shippingCountry() + ".");
        }
    }
}
