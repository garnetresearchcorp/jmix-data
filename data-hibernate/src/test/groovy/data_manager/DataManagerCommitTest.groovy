/*
 * Copyright 2019 Haulmont.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package data_manager

import io.jmix.core.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import test_support.DataSpec
import test_support.TestOrderChangedEventListener
import test_support.entity.TestAppEntity
import test_support.entity.TestAppEntityItem
import test_support.entity.TestSecondAppEntity
import test_support.entity.nullable_id.Foo
import test_support.entity.nullable_id.FooPart
import test_support.entity.sales.Customer
import test_support.entity.sales.Order

class DataManagerCommitTest extends DataSpec {

    @Autowired
    DataManager dataManager

    @Autowired
    EntityStates entityStates

    @Autowired
    TestOrderChangedEventListener orderChangedEventListener

    @Autowired
    JdbcTemplate jdbcTemplate

    @Autowired
    FetchPlans fetchPlans

    TestAppEntity appEntity
    TestAppEntityItem appEntityItem


    void setup() {
        appEntity = dataManager.create(TestAppEntity.class)
        appEntity.name = 'appEntity'

        appEntityItem = dataManager.create(TestAppEntityItem.class)
        appEntityItem.name = 'appEntityItem'
        appEntityItem.appEntity = appEntity

        dataManager.save(appEntity, appEntityItem)
    }

    void cleanup() {
        jdbcTemplate.update('delete from SALES_ORDER')
        jdbcTemplate.update('delete from SALES_CUSTOMER')
        jdbcTemplate.update('delete from TEST_NULLABLE_ID_FOO_PART')
        jdbcTemplate.update('delete from TEST_NULLABLE_ID_FOO')
    }


    def "test view after commit"() {
        when:

        def view = fetchPlans.builder(TestAppEntity.class)
                .add("createTs")
                .add("items.createTs")
                .partial()
                .build()

        def loadedAppEntity = dataManager.load(Id.of(appEntity)).fetchPlan(view).one()

        then:

        entityStates.isLoaded(loadedAppEntity, 'items')
        loadedAppEntity.items[0] != null

        when:

        def entity = dataManager.create(TestSecondAppEntity.class)
        entity.name = 'secondAppEntity'
        entity.appEntity = loadedAppEntity

        def commitView = fetchPlans.builder(TestSecondAppEntity.class)
                .add("name")
                .add("appEntity.createTs")
                .add("appEntity.items.name")
                .build()

        def entity1 = dataManager.save(new SaveContext().saving(entity, commitView)).get(entity)

        then:

        entityStates.isLoaded(entity1.appEntity, 'createTs')
        entityStates.isLoaded(entity1.appEntity.items[0], 'name')
    }

    def "commit returns object fetched according to passed view even if it was reloaded in EntityChangedEvent listener"() {
        given:
        def customer = dataManager.create(Customer)
        customer.name = 'c1'

        customer = dataManager.save(customer)

        def order = dataManager.create(Order)
        order.number = '1'
        order.customer = customer

        orderChangedEventListener.enabled = true

        when:
        def fetchPlan = fetchPlans.builder(Order).addFetchPlan(FetchPlan.LOCAL).add('customer.name').build()
        def committedOrder = dataManager.save(new SaveContext().saving(order, fetchPlan)).get(order)

        then:
        entityStates.isLoaded(committedOrder, 'customer')
        committedOrder.customer.name == customer.name

        cleanup:
        orderChangedEventListener.enabled = false
    }

    def "save entities with null id"() {
        def foo = dataManager.create(Foo)
        foo.name = 'foo'

        foo = dataManager.save(foo)

        def part = dataManager.create(FooPart)
        part.name = 'p1'
        part.foo = foo

        foo.parts = [part]

        when:
        def savedSet = dataManager.save(foo, part)
        def foo1 = dataManager.load(Id.of(foo)).fetchPlan { fp -> fp.addAll('parts.name') }.one()

        then:
        savedSet.size() == 2
        savedSet.get(foo) == foo
        savedSet.get(part) == part

        foo1.parts.size() == 1
        foo1.parts[0] == part
    }

    def "save entity with removed reference"() {
        when:

        def customer = dataManager.create(Customer)
        dataManager.remove(dataManager.save(customer))
        def committedCustomer = dataManager.load(Customer).id(customer.id)
                .softDeletion(false)
                .optional().orElse(null)

        def order = dataManager.create(Order)
        order.number = '1'
        order.customer = committedCustomer

        SaveContext commitContext = new SaveContext().saving(order)
        commitContext.setSoftDeletion(false)
        EntitySet committedEntities = dataManager.save(commitContext)

        def committedOrder = committedEntities.get(Order, order.id)

        then:
        committedOrder.customer == customer
    }
}
