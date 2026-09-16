package com.enterpriseapp.procureflow.vendor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.enterpriseapp.procureflow.common.exception.InvalidStateTransitionException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VendorServiceTest {

  @Mock private VendorRepository vendorRepository;

  private VendorService vendorService;

  @BeforeEach
  void setUp() {
    vendorService = new VendorService(vendorRepository);
  }

  @Test
  void approveActivatesAPendingVendor() {
    Vendor vendor =
        Vendor.builder()
            .name("Acme")
            .contactEmail("sales@acme.test")
            .status(VendorStatus.PENDING_APPROVAL)
            .build();
    vendor.setId(1L);
    when(vendorRepository.findById(1L)).thenReturn(Optional.of(vendor));

    Vendor approved = vendorService.approve(1L);

    assertThat(approved.getStatus()).isEqualTo(VendorStatus.ACTIVE);
  }

  @Test
  void approveRejectsAVendorThatIsNotPending() {
    Vendor vendor =
        Vendor.builder()
            .name("Acme")
            .contactEmail("sales@acme.test")
            .status(VendorStatus.ACTIVE)
            .build();
    vendor.setId(2L);
    when(vendorRepository.findById(2L)).thenReturn(Optional.of(vendor));

    assertThatThrownBy(() -> vendorService.approve(2L))
        .isInstanceOf(InvalidStateTransitionException.class);
  }
}
