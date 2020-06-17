package org.tests.query;

import io.ebean.BaseTestCase;
import io.ebean.DB;
import io.ebean.Query;
import io.ebean.QueryIterator;
import io.ebean.datasource.DataSourcePool;
import io.ebean.plugin.SpiServer;
import org.ebeantest.LoggedSqlCollector;
import org.junit.Test;
import org.tests.model.basic.Customer;
import org.tests.model.basic.Order;
import org.tests.model.basic.OrderShipment;
import org.tests.model.basic.ResetBasicData;

import javax.persistence.PersistenceException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TestQueryFindIterate extends BaseTestCase {

  @Test
  public void test() {

    ResetBasicData.reset();

    Query<Customer> query = DB.find(Customer.class)
      .setMaxRows(2);

    final AtomicInteger count = new AtomicInteger();

    try (QueryIterator<Customer> it = query.findIterate()) {
      while (it.hasNext()) {
        Customer customer = it.next();
        customer.getName();
        count.incrementAndGet();
      }
    }

    assertEquals(2, count.get());
  }

  @Test
  public void test_hasNext_hasNext() {

    ResetBasicData.reset();
    QueryIterator<Customer> queryIterator = DB.find(Customer.class)
      .where()
      .isNotNull("name")
      .setMaxRows(3)
      .order().asc("id")
      .findIterate();

    try {
      // check that hasNext does not move to the next bean
      assertTrue(queryIterator.hasNext());
      assertTrue(queryIterator.hasNext());
      assertTrue(queryIterator.hasNext());
      assertTrue(queryIterator.hasNext());
      assertTrue(queryIterator.hasNext());

      Customer first = queryIterator.next();
      logger.info("first: {}", queryIterator.next());
      assertEquals(first.getId(), Integer.valueOf(1));

      while (queryIterator.hasNext()) {
        logger.info("next: {}", queryIterator.next());
      }

    } finally {
      queryIterator.close();
    }
  }

  @Test
  public void findEach() {

    ResetBasicData.reset();

    Query<Customer> query = DB.find(Customer.class)
      .setAutoTune(false)
      //.fetch("contacts", new FetchConfig().query(2)).where().gt("id", 0).orderBy("id")
      .setMaxRows(2);

    final AtomicInteger count = new AtomicInteger();

    query.findEach(bean -> count.incrementAndGet());

    assertEquals(2, count.get());
  }

  @Test
  public void testWithLazyLoading() {

    ResetBasicData.reset();

    DB.find(Order.class)
      //.select("orderDate")
      .where().gt("id", 0).le("id", 10)
      .findEach(order -> {
        Customer customer = order.getCustomer();
        // invoke lazy loading on customer, order details and order shipments
        order.getId();
        customer.getName();
        order.getDetails().size();
        order.getShipments().size();
      });

  }

  @Test
  public void testWithLazyBatchSize() {

    ResetBasicData.reset();

    LoggedSqlCollector.start();

    DB.find(Order.class)
      .setLazyLoadBatchSize(10)
      .select("status, orderDate")
      .fetch("customer", "name")
      .where().gt("id", 0).le("id", 10)
      .setUseCache(false)
      .findEach(order -> {
        Customer customer = order.getCustomer();
        customer.getName();
        order.getDetails().size();
        order.getShipments().size();
      });


    List<String> loggedSql = LoggedSqlCollector.stop();

    assertEquals(3, loggedSql.size());
    assertTrue(trimSql(loggedSql.get(0), 7).contains("select t0.id, t0.status, t0.order_date, t1.id, t1.name from o_order t0 join o_customer t1"));
    assertTrue(trimSql(loggedSql.get(1), 7).contains("select t0.order_id, t0.id, t0.order_qty, t0.ship_qty, t0.unit_price"));
    assertTrue(trimSql(loggedSql.get(2), 7).contains("select t0.order_id, t0.id, t0.ship_time, t0.cretime, t0.updtime, t0.version, t0.order_id from or_order_ship"));
  }

  @Test
  public void testWithTwoJoins() {

    ResetBasicData.reset();

    LoggedSqlCollector.start();

    // make sure we don't hit the L2 cache for order shipments
    DB.getServerCacheManager().clear(Order.class);
    DB.getServerCacheManager().clear(OrderShipment.class);

    DB.find(Order.class)
      .setLazyLoadBatchSize(10)
      .setUseCache(false)
      .select("status, orderDate")
      .fetch("customer", "name")
      .fetch("details")
      .where().gt("id", 0).le("id", 10)
      .order().asc("id")
      .findEach(order -> {
        Customer customer = order.getCustomer();
        order.getId();
        customer.getName();
        order.getDetails().size();
        order.getShipments().size();
      });

    List<String> loggedSql = LoggedSqlCollector.stop();

    assertEquals("Got SQL: " + loggedSql, 2, loggedSql.size());
    assertThat(trimSql(loggedSql.get(0), 7).contains("select t0.id, t0.status, t0.order_date, t1.id, t1.name, t2.id, t2.order_qty, t2.ship_qty"));
    assertThat(trimSql(loggedSql.get(1), 7).contains("select t0.order_id, t0.id, t0.ship_time, t0.cretime, t0.updtime, t0.version, t0.order_id from or_order_ship"));
  }

  @Test(expected = PersistenceException.class)
  public void testWithExceptionInQuery() {

    ResetBasicData.reset();

    // intentionally a query with incorrect type binding
    Query<Customer> query = DB.find(Customer.class)
      .setAutoTune(false)
      .where().gt("id", "JUNK_NOT_A_LONG")
      .setMaxRows(2).query();

    // this throws an exception immediately
    query.findEach(bean -> {

    });

    if (!DB.getDefault().getName().equals("h2")) {
      // MySql allows the query with type conversion?
      throw new PersistenceException("H2 does expected thing but MySql does not");
    }
    fail("Never get here as exception thrown");
  }


  @Test(expected = IllegalStateException.class)
  public void testWithExceptionInLoop() {

    ResetBasicData.reset();

    Query<Customer> query = DB.find(Customer.class)
      .setAutoTune(false)
      .where().gt("id", 0)
      .setMaxRows(2).query();

    query.findEach(customer -> {
      if (customer != null) {
        throw new IllegalStateException("cause an exception");
      }
    });
  }

  @Test
  public void testCloseConnection() {
    ResetBasicData.reset();

    SpiServer pluginApi = server().getPluginApi();
    DataSourcePool dsPool = (DataSourcePool) pluginApi.getServerConfig().getReadOnlyDataSource();
    if (dsPool == null) {
      dsPool = (DataSourcePool) server().getPluginApi().getDataSource();
    }

    int startConns = dsPool.getStatus(false).getBusy();
    QueryIterator<Customer> queryIterator = server().find(Customer.class)
        .where()
        .isNotNull("name")
        .setMaxRows(3)
        .order().asc("id")
        .findIterate();

    assertThat(dsPool.getStatus(false).getBusy()).isEqualTo(startConns + 1);

    assertTrue(queryIterator.hasNext());
    assertThat(queryIterator.next()).isNotNull();
    assertThat(dsPool.getStatus(false).getBusy()).isEqualTo(startConns + 1);

    assertTrue(queryIterator.hasNext());
    assertThat(queryIterator.next()).isNotNull();
    assertThat(dsPool.getStatus(false).getBusy()).isEqualTo(startConns + 1);

    assertTrue(queryIterator.hasNext());
    assertThat(queryIterator.next()).isNotNull();
    assertThat(dsPool.getStatus(false).getBusy()).isEqualTo(startConns + 1);

    assertFalse(queryIterator.hasNext());
    assertThat(dsPool.getStatus(false).getBusy()).isEqualTo(startConns);
    try  {
      queryIterator.next();
      fail("noSuchElementException expected");
    } catch (NoSuchElementException e) {
      logger.debug("Expected NoSuchElementException");
    }
  }
}
