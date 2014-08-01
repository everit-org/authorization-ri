/**
 * This file is part of Everit - Authorization Tests.
 *
 * Everit - Authorization Tests is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Everit - Authorization Tests is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Everit - Authorization Tests.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.everit.osgi.authorization.ri.tests;

import java.util.concurrent.ConcurrentMap;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.everit.osgi.cache.CacheConfiguration;
import org.everit.osgi.cache.CacheFactory;
import org.everit.osgi.cache.CacheHolder;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;

@Component
@Properties({ @Property(name = "cacheName", value = "testCache") })
@Service
public class TestCache implements CacheFactory, CacheConfiguration<Object, Object> {

    @Override
    public <K, V> CacheHolder<K, V> createCache(CacheConfiguration<K, V> configuration, ClassLoader classLoader) {
        // TODO Auto-generated method stub
        return new CacheHolder<K, V>() {

            private final ConcurrentMap<K, V> cache = new ConcurrentLinkedHashMap.Builder().maximumWeightedCapacity(
                    1000)
                    .build();

            @Override
            public void close() {
                // TODO Auto-generated method stub

            }

            @Override
            public ConcurrentMap<K, V> getCache() {
                return cache;
            }
        };
    }

}
