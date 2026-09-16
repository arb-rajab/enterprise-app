package com.enterpriseapp.procureflow.requisition;

import com.enterpriseapp.procureflow.requisition.dto.ApprovalDecisionRequest;
import com.enterpriseapp.procureflow.requisition.dto.CreateRequisitionRequest;
import com.enterpriseapp.procureflow.requisition.dto.RequisitionResponse;
import com.enterpriseapp.procureflow.user.RoleName;
import com.enterpriseapp.procureflow.user.User;
import com.enterpriseapp.procureflow.user.UserService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/requisitions")
@RequiredArgsConstructor
public class RequisitionController {

  private final RequisitionService requisitionService;
  private final UserService userService;

  @GetMapping
  public List<RequisitionResponse> findAll() {
    return requisitionService.findAll().stream().map(RequisitionResponse::from).toList();
  }

  @GetMapping("/pending-my-approval")
  public List<RequisitionResponse> pendingMyApproval(Authentication authentication) {
    Set<RoleName> roles = rolesOf(authentication);
    return requisitionService.findPendingApprovalFor(roles).stream()
        .map(RequisitionResponse::from)
        .toList();
  }

  @GetMapping("/{id}")
  public RequisitionResponse findById(@PathVariable Long id) {
    return RequisitionResponse.from(requisitionService.findById(id));
  }

  @PostMapping
  public ResponseEntity<RequisitionResponse> create(
      @Valid @RequestBody CreateRequisitionRequest request, Authentication authentication) {
    User requester = currentUser(authentication);
    PurchaseRequisition created = requisitionService.create(request, requester);
    return ResponseEntity.created(URI.create("/api/v1/requisitions/" + created.getId()))
        .body(RequisitionResponse.from(created));
  }

  @PostMapping("/{id}/submit")
  public RequisitionResponse submit(@PathVariable Long id, Authentication authentication) {
    return RequisitionResponse.from(requisitionService.submit(id, currentUser(authentication)));
  }

  @PostMapping("/{id}/decide")
  public RequisitionResponse decide(
      @PathVariable Long id,
      @Valid @RequestBody ApprovalDecisionRequest request,
      Authentication authentication) {
    return RequisitionResponse.from(
        requisitionService.decide(id, request, currentUser(authentication)));
  }

  @PostMapping("/{id}/cancel")
  public RequisitionResponse cancel(@PathVariable Long id, Authentication authentication) {
    return RequisitionResponse.from(requisitionService.cancel(id, currentUser(authentication)));
  }

  private User currentUser(Authentication authentication) {
    return userService.findByEmail(authentication.getName());
  }

  private Set<RoleName> rolesOf(Authentication authentication) {
    return authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .map(RoleName::valueOf)
        .collect(Collectors.toSet());
  }
}
