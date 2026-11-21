package com.justfast.identity.web;

import com.justfast.identity.application.AddressService;
import com.justfast.identity.domain.Address;
import com.justfast.identity.domain.Address.AddressDetails;
import com.justfast.identity.security.AppUserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account/addresses")
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping
    public List<AddressResponse> list(@AuthenticationPrincipal AppUserPrincipal user) {
        return addressService.list(user.id()).stream().map(AddressResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AddressResponse create(@AuthenticationPrincipal AppUserPrincipal user, @Valid @RequestBody AddressRequest request) {
        return AddressResponse.from(addressService.create(user.id(), request.toDetails()));
    }

    @PutMapping("/{id}")
    public AddressResponse update(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable Long id,
                                  @Valid @RequestBody AddressRequest request) {
        return AddressResponse.from(addressService.update(user.id(), id, request.toDetails()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable Long id) {
        addressService.delete(user.id(), id);
    }

    public record AddressRequest(
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Size(max = 200) String line1,
            @Size(max = 200) String line2,
            @NotBlank @Size(max = 100) String city,
            @Size(max = 100) String region,
            @NotBlank @Size(max = 20) String postalCode,
            @NotBlank @Pattern(regexp = "[A-Za-z]{2}", message = "Use a two-letter country code.") String country,
            @Size(max = 30) String phone) {

        AddressDetails toDetails() {
            return new AddressDetails(fullName, line1, line2, city, region, postalCode, country, phone);
        }
    }

    public record AddressResponse(Long id, String fullName, String line1, String line2, String city, String region,
                                  String postalCode, String country, String phone) {

        static AddressResponse from(Address address) {
            AddressDetails d = address.details();
            return new AddressResponse(address.getId(), d.fullName(), d.line1(), d.line2(), d.city(), d.region(),
                    d.postalCode(), d.country(), d.phone());
        }
    }
}
