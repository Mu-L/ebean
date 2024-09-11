package io.ebeaninternal.server.changelog;

import io.ebean.event.changelog.ChangeLogFilter;
import org.junit.jupiter.api.Test;
import org.tests.model.basic.Address;
import org.tests.model.basic.Contact;
import org.tests.model.basic.Country;
import org.tests.model.basic.Customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class DefaultChangeLogRegisterTest {

  @Test
  void test_defaultInsertTrue() {

    DefaultChangeLogRegister register = new DefaultChangeLogRegister(true);

    assertNull(register.getChangeFilter(Address.class));

    ChangeLogFilter changeFilter = register.getChangeFilter(Customer.class);
    DefaultChangeLogRegister.UpdateFilter updateFilter = (DefaultChangeLogRegister.UpdateFilter) changeFilter;
    assertFalse(updateFilter.includeInserts);
    assertThat(updateFilter.updateProperties).containsExactly("name", "status");

    changeFilter = register.getChangeFilter(Country.class);
    DefaultChangeLogRegister.BasicFilter countryFilter = (DefaultChangeLogRegister.BasicFilter) changeFilter;
    assertTrue(countryFilter.includeInserts);

    // use default setting
    changeFilter = register.getChangeFilter(Contact.class);
    DefaultChangeLogRegister.BasicFilter contactFilter = (DefaultChangeLogRegister.BasicFilter) changeFilter;
    assertTrue(contactFilter.includeInserts);

  }

  @Test
  void test_defaultInsertFalse() {

    DefaultChangeLogRegister register = new DefaultChangeLogRegister(false);

    assertNull(register.getChangeFilter(Address.class));

    ChangeLogFilter changeFilter = register.getChangeFilter(Customer.class);
    DefaultChangeLogRegister.UpdateFilter updateFilter = (DefaultChangeLogRegister.UpdateFilter) changeFilter;
    assertFalse(updateFilter.includeInserts);
    assertThat(updateFilter.updateProperties).containsExactly("name", "status");

    changeFilter = register.getChangeFilter(Country.class);
    DefaultChangeLogRegister.BasicFilter countryFilter = (DefaultChangeLogRegister.BasicFilter) changeFilter;
    assertTrue(countryFilter.includeInserts);

    // use default setting
    changeFilter = register.getChangeFilter(Contact.class);
    DefaultChangeLogRegister.BasicFilter contactFilter = (DefaultChangeLogRegister.BasicFilter) changeFilter;
    assertFalse(contactFilter.includeInserts);

  }

}
