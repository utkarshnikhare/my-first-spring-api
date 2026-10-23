package com.example.my_first_spring_api.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.Set;
import java.util.TreeSet;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:probe;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false","spring.jpa.show-sql=true","spring.jpa.properties.hibernate.format_sql=false"
})
class SchemaProbeTest {
    @Autowired EntityManager em;

    @Test
    void listEntitiesAndTables() {
        EntityManagerFactory f = em.getEntityManagerFactory();
        Set<String> names = new TreeSet<>(f.getMetamodel().getEntities().stream()
                .map(e -> e.getJavaType().getName()).toList());
        System.out.println("PROBE_ENTITIES=" + names);
        try {
            var rows = em.createNativeQuery("select table_name from information_schema.tables where table_schema='PUBLIC'").getResultList();
            System.out.println("PROBE_TABLES=" + rows);
        } catch (Exception e) {
            System.out.println("PROBE_TABLES_ERR=" + e.getMessage());
        }
    }
}