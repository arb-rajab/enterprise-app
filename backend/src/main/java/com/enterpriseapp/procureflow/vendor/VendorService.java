package com.enterpriseapp.procureflow.vendor;

import com.enterpriseapp.procureflow.common.exception.InvalidStateTransitionException;
import com.enterpriseapp.procureflow.common.exception.ResourceNotFoundException;
import com.enterpriseapp.procureflow.vendor.dto.VendorRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VendorService {

  private final VendorRepository vendorRepository;

  public List<Vendor> findAll() {
    return vendorRepository.findAll();
  }

  public Vendor findById(Long id) {
    return vendorRepository
        .findById(id)
        .orElseThrow(() -> ResourceNotFoundException.of("Vendor", id));
  }

  @Transactional
  public Vendor create(VendorRequest request) {
    Vendor vendor =
        Vendor.builder()
            .name(request.name())
            .contactEmail(request.contactEmail())
            .phone(request.phone())
            .address(request.address())
            .taxId(request.taxId())
            .status(VendorStatus.PENDING_APPROVAL)
            .build();
    return vendorRepository.save(vendor);
  }

  @Transactional
  public Vendor update(Long id, VendorRequest request) {
    Vendor vendor = findById(id);
    vendor.setName(request.name());
    vendor.setContactEmail(request.contactEmail());
    vendor.setPhone(request.phone());
    vendor.setAddress(request.address());
    vendor.setTaxId(request.taxId());
    return vendor;
  }

  @Transactional
  public Vendor approve(Long id) {
    Vendor vendor = findById(id);
    if (vendor.getStatus() != VendorStatus.PENDING_APPROVAL) {
      throw new InvalidStateTransitionException(
          "Vendor " + id + " is not pending approval (current status: " + vendor.getStatus() + ")");
    }
    vendor.setStatus(VendorStatus.ACTIVE);
    return vendor;
  }

  @Transactional
  public Vendor deactivate(Long id) {
    Vendor vendor = findById(id);
    vendor.setStatus(VendorStatus.INACTIVE);
    return vendor;
  }
}
