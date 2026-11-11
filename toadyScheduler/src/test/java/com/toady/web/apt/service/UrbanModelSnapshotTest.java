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

    @Test void ordinaryModelSnapshotRemovesMissingAndEmptyModels() {
        var first = aptModel("1", "50"); var second = aptModel("2", "60");
        persistence.replaceModels(com.toady.web.apt.entity.AptLttotPblancMdl.class, List.of(key), List.of(first, second));
        persistence.replaceModels(com.toady.web.apt.entity.AptLttotPblancMdl.class, List.of(key), List.of(second));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM TB_APT_LTTOT_PBLANC_MDL", Integer.class));
        persistence.replaceModels(com.toady.web.apt.entity.AptLttotPblancMdl.class, List.of(key), List.of());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM TB_APT_LTTOT_PBLANC_MDL", Integer.class));
    }
    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void ordinarySnapshotRestoresDeletedRowsWhenInsertFails() {
        persistence.replaceModels(com.toady.web.apt.entity.AptLttotPblancMdl.class, List.of(key), List.of(aptModel("1", "50")));
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () ->
                persistence.replaceModels(com.toady.web.apt.entity.AptLttotPblancMdl.class, List.of(key), List.of(aptModel("2", "area-exceeds-column-length-17"))));
        assertEquals("50", jdbc.queryForObject("SELECT SUPLY_AR FROM TB_APT_LTTOT_PBLANC_MDL WHERE MODEL_NO = '1'", String.class));
        jdbc.update("DELETE FROM TB_APT_LTTOT_PBLANC_MDL");
    }
    private com.toady.web.apt.entity.AptLttotPblancMdl aptModel(String number, String area) {
        var model = new com.toady.web.apt.entity.AptLttotPblancMdl();
        model.setHouseManageNo(key.houseManageNo()); model.setPblancNo(key.pblancNo());
        model.setModelNo(number); model.setSuplyAr(area); return model;
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(classes = {
            com.toady.web.apt.entity.AptLttotPblancMdl.class,
            com.toady.web.apt.entity.RemndrLttotPblancMdl.class,
            com.toady.web.apt.entity.PblPvtRentLttotPblancMdl.class,
            com.toady.web.apt.entity.OptLttotPblancMdl.class
    })
    <T> void ordinarySnapshotIsScopedToTheFetchedAnnouncement(Class<T> type) throws Exception {
        var other = new AnnouncementKey("OTHER", "NOTICE");
        T first = type.getDeclaredConstructor().newInstance();
        T second = type.getDeclaredConstructor().newInstance();
        for (var pair : List.of(new Object[]{first, key}, new Object[]{second, other})) {
            var bean = new org.springframework.beans.BeanWrapperImpl(pair[0]);
            var scope = (AnnouncementKey) pair[1];
            bean.setPropertyValue("houseManageNo", scope.houseManageNo());
            bean.setPropertyValue("pblancNo", scope.pblancNo());
            bean.setPropertyValue("modelNo", "1");
        }
        persistence.replaceModels(type, List.of(key, other), List.of(first, second));
        persistence.replaceModels(type, List.of(key), List.of());
        String table = type.getAnnotation(jakarta.persistence.Table.class).name();
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE HOUSE_MANAGE_NO='OTHER'", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE HOUSE_MANAGE_NO='SNAPSHOT'", Integer.class));
    }
}
