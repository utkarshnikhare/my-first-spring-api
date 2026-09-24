package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.dto.*;
import com.example.my_first_spring_api.exception.*;
import com.example.my_first_spring_api.model.*;
import com.example.my_first_spring_api.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:requirements-test;DB_CLOSE_DELAY=-1","spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.open-in-view=false"})
@ActiveProfiles("test")
class SocioMartRequirementsIntegrationTest {
 @Autowired OrderService orders; @Autowired SellerService sellerService; @Autowired SellerAppService sellerApp; @Autowired AdminService admin;
 @Autowired UserRepository users; @Autowired KitchenRepository kitchens; @Autowired ProductRepository products; @Autowired OrderRepository orderRepo; @Autowired PlatformTransactionManager tx;
 private Long orderId,sellerId,buyerId,otherId,productId; private User seller,buyer,other;
 @BeforeEach void setup(){String s=UUID.randomUUID().toString().substring(0,4);Long[] ids=new TransactionTemplate(tx).execute(x->{User a=users.save(new User("Seller"+s,"91"+s+"0001",null,UserRole.SELLER));a.setSellerApprovalStatus(SellerApprovalStatus.APPROVED);User o=users.save(new User("Other"+s,"92"+s+"0001",null,UserRole.SELLER));User b=users.save(new User("Buyer"+s,"93"+s+"0001","A-1",UserRole.BUYER));b.setSociety("Society");b.setBuilding("A");Kitchen k=kitchens.save(new Kitchen("k"+s,"Kitchen "+s,"",null,a));Product pr=products.save(new Product(k,"Poha","",BigDecimal.valueOf(25),null));pr.setAvailableToday(true);pr.setRemainingQuantity(50);products.save(pr);Order ord=new Order(b,k);ord.setOrderNumber("SM"+s);ord.addItem(new OrderItem(pr,2,BigDecimal.valueOf(25)));ord.recalculateTotal();orderRepo.save(ord);return new Long[]{ord.getId(),a.getId(),b.getId(),o.getId(),pr.getId()};});orderId=ids[0];sellerId=ids[1];buyerId=ids[2];otherId=ids[3];productId=ids[4];seller=users.findById(sellerId).orElseThrow();buyer=users.findById(buyerId).orElseThrow();other=users.findById(otherId).orElseThrow();}
 @Test void phoneSummaryDetailAndOwnership() throws Exception{SellerOrderSummaryDto summary=sellerApp.getOrderSummary(seller,LocalDate.now());OrderItemDetailDto drill=sellerApp.getOrderItemDetail(seller,productId,LocalDate.now(),null,null);List<SellerOrderSummaryRowDto> list=sellerService.getMyOrders(seller);ObjectMapper json=new ObjectMapper().registerModule(new JavaTimeModule());assertThat(json.writeValueAsString(summary)).doesNotContain("mobileNumber","buyerMobile");assertThat(json.writeValueAsString(drill)).doesNotContain("mobileNumber","buyerMobile");assertThat(json.writeValueAsString(list)).doesNotContain("mobileNumber","buyerMobile");assertThat(orders.getOrderDtoForSeller(orderId,seller).getBuyer().getMobileNumber()).isEqualTo(buyer.getMobileNumber());assertThatThrownBy(()->orders.getOrderDtoForSeller(orderId,other)).isInstanceOf(SellerNotAuthorizedException.class);}
 @Test void deferredPaymentPersistsAndSellerPaidIsIdempotent(){MockHttpSession s=new MockHttpSession();s.setAttribute(OrderService.DRAFT_ORDER_SESSION_KEY,orderId);s.setAttribute("BUYER_USER",buyerId);assertThat(orders.placeOrder(PaymentStatus.WILL_PAY_LATER,null,null,s).getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);assertThat(orderRepo.findById(orderId).orElseThrow().getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);assertThatThrownBy(()->orders.markOrderAsPaid(orderId,other)).isInstanceOf(SellerNotAuthorizedException.class);assertThatThrownBy(()->orders.markOrderAsPaid(999999L,seller)).isInstanceOf(OrderNotFoundException.class);long count=orderRepo.count();BigDecimal total=orderRepo.findById(orderId).orElseThrow().getTotalAmount();assertThat(orders.markOrderAsPaid(orderId,seller).getPaymentStatus()).isEqualTo(PaymentStatus.PAID);OrderDto buyerView=orders.getOrderDetails(orderId,buyer);assertThat(buyerView.getOrderStatus()).isEqualTo(OrderStatus.ORDERED);assertThat(buyerView.getTotalAmount()).isEqualByComparingTo(total);assertThat(buyerView.getItems().get(0).getQuantity()).isEqualTo(2);assertThat(buyerView.getItems().get(0).getPrice()).isEqualByComparingTo(BigDecimal.valueOf(25));assertThat(buyerView.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);assertThat(admin.orderDetail(orderId).get("paymentStatus")).isEqualTo("PAID");assertThat(orderRepo.count()).isEqualTo(count);orders.markOrderAsPaid(orderId,seller);assertThat(orderRepo.count()).isEqualTo(count);assertThat(orderRepo.findById(orderId).orElseThrow().getPaymentStatus()).isEqualTo(PaymentStatus.PAID);}
 @Test void cancelledAndLegacyDeferredAreSafe(){Order ord=orderRepo.findById(orderId).orElseThrow();ord.setOrderStatus(OrderStatus.CANCELLED);orderRepo.save(ord);assertThatThrownBy(()->orders.markOrderAsPaid(orderId,seller)).isInstanceOf(IllegalArgumentException.class);Kitchen k=kitchens.findBySeller(seller).get(0);Product p=products.findById(productId).orElseThrow();Order legacy=new Order(buyer,k);legacy.setOrderNumber("LEGACY-"+UUID.randomUUID());legacy.setOrderStatus(OrderStatus.ORDERED);legacy.setPaymentStatus(PaymentStatus.WILL_PAY_LATER);legacy.addItem(new OrderItem(p,1,BigDecimal.valueOf(25)));legacy.recalculateTotal();orderRepo.save(legacy);assertThat(orders.markOrderAsPaid(legacy.getId(),seller).getPaymentStatus()).isEqualTo(PaymentStatus.PAID);}
}
