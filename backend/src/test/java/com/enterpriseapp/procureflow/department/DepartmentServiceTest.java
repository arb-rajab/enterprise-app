package com.enterpriseapp.procureflow.department;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.enterpriseapp.procureflow.common.exception.DuplicateResourceException;
import com.enterpriseapp.procureflow.department.dto.DepartmentRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DepartmentServiceTest {

  @Mock private DepartmentRepository departmentRepository;

  private DepartmentService departmentService;

  @BeforeEach
  void setUp() {
    departmentService = new DepartmentService(departmentRepository);
  }

  @Test
  void createRejectsDuplicateCode() {
    DepartmentRequest request = new DepartmentRequest("ENG", "Engineering", "CC-100", null);
    when(departmentRepository.existsByCode("ENG")).thenReturn(true);

    assertThatThrownBy(() -> departmentService.create(request))
        .isInstanceOf(DuplicateResourceException.class);
  }

  @Test
  void createSavesANewDepartmentWhenCodeIsUnique() {
    DepartmentRequest request = new DepartmentRequest("SALES", "Sales", "CC-200", null);
    when(departmentRepository.existsByCode("SALES")).thenReturn(false);
    when(departmentRepository.save(any(Department.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Department created = departmentService.create(request);

    assertThat(created.getCode()).isEqualTo("SALES");
    assertThat(created.getName()).isEqualTo("Sales");
  }
}
