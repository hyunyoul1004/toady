package com.toady.web.apt.service;

import com.toady.web.apt.entity.UrbtyOfctlLttotPblancMdl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest(showSql = false, properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl"
})
@Import(ApplyhomeDataPersistenceService.class)
class UrbanModelSnapshotTest {
    @Autowired ApplyhomeDataPersistenceService persistence;
    @Autowired JdbcTemplate jdbc;
    private final AnnouncementKey key = new AnnouncementKey("SNAPSHOT", "SNAPSHOT");

    private UrbtyOfctlLttotPblancMdl model(String type, String price) {
        var model = new UrbtyOfctlLttotPblancMdl();
        model.setHouseManageNo(key.houseManageNo());
        model.setPblancNo(key.pblancNo());
        model.setModelNo("1");
        model.setTp(type);
        model.setSuplyAmount(price);
        return model;
    }

    @Test void replacesChangedPricesAndKeepsUnchangedIds() {
        persistence.upsertUrbanModels(List.of(key), List.of(model("A", "100"), model("B", "150")));
        Long unchangedId = jdbc.queryForObject("select ID from TB_URBTY_OFCTL_LTTOT_PBLANC_MDL where TP = 'B'", Long.class);
        persistence.upsertUrbanModels(List.of(key), List.of(model("A", "200"), model("B", "150")));
        assertEquals(unchangedId, jdbc.queryForObject("select ID from TB_URBTY_OFCTL_LTTOT_PBLANC_MDL where TP = 'B'", Long.class));
        assertEquals(2, count());
        assertEquals("200", jdbc.queryForObject("select SUPLY_AMOUNT from TB_URBTY_OFCTL_LTTOT_PBLANC_MDL where TP = 'A'", String.class));
        persistence.upsertUrbanModels(List.of(key), List.of(model("A", "200"), model("B", "150")));
        assertEquals(2, count());
    }

    @Test void removesMissingModelsAndAnEmptySuccessfulSnapshot() {
        persistence.upsertUrbanModels(List.of(key), List.of(model("A", "100"), model("B", "150")));
        persistence.upsertUrbanModels(List.of(key), List.of(model("B", "150")));
        assertEquals(1, count());
        persistence.upsertUrbanModels(List.of(key), List.of());
        assertEquals(0, count());
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void rollsBackEarlierAnnouncementsIfALaterSnapshotFails() {
        var first = new AnnouncementKey("ROLLBACK", "ROLLBACK");
        var second = new AnnouncementKey("INVALID", "INVALID");
        var original = model("A", "100");
        original.setHouseManageNo(first.houseManageNo());
        original.setPblancNo(first.pblancNo());
        persistence.upsertUrbanModels(List.of(first), List.of(original));
        try {
            var updated = model("A", "200");
            updated.setHouseManageNo(first.houseManageNo());
            updated.setPblancNo(first.pblancNo());
            var invalid = model("type-exceeds-column-length", "300");
            invalid.setHouseManageNo(second.houseManageNo());
            invalid.setPblancNo(second.pblancNo());
            org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                    () -> persistence.upsertUrbanModels(List.of(first, second), List.of(updated, invalid)));
            assertEquals("100", jdbc.queryForObject(
                    "select SUPLY_AMOUNT from TB_URBTY_OFCTL_LTTOT_PBLANC_MDL where HOUSE_MANAGE_NO = 'ROLLBACK'", String.class));
            assertEquals(0, jdbc.queryForObject(
                    "select count(*) from TB_URBTY_OFCTL_LTTOT_PBLANC_MDL where HOUSE_MANAGE_NO = 'INVALID'", Integer.class));
        } finally {
            jdbc.update("delete from TB_URBTY_OFCTL_LTTOT_PBLANC_MDL where HOUSE_MANAGE_NO in ('ROLLBACK', 'INVALID')");
        }
    }

    private int count() {
        return jdbc.queryForObject("select count(*) from TB_URBTY_OFCTL_LTTOT_PBLANC_MDL", Integer.class);
    }
}
