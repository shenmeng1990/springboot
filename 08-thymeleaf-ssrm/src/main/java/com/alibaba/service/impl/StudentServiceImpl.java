package com.alibaba.service.impl;

import com.alibaba.dao.StudentDao;
import com.alibaba.model.Student;
import com.alibaba.service.StudentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.BoundValueOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * @Author shenmeng
 * @Date 2020/6/12
 **/
@Service
@Slf4j
public class StudentServiceImpl implements StudentService {

    @Autowired
    private StudentDao studentDao;
    
    @Autowired
    private RedisTemplate<Object,Object> redisTemplate;

    @Override
    @CacheEvict(value = "realTimeCache", allEntries = true)
    public void saveStudent(Student student) {
        studentDao.insertStudent(student);
    }

    @Override
    @Cacheable(value = "realTimeCache", key = "'student_'+#id")
    public Student findById(int id) {
        System.out.println("数据库查询");
        return studentDao.selectById(id);
    }

    // Redis在高并发情况下可能会存在哪些问题？
    // 1） 缓存穿透：当从DB中查询结果为null时有可能会引发缓存穿透问题。
    //    其解决方案是为这些为null的结果赋予一个默认值
    // 2） 缓存雪崩：当缓存中的某些缓存在同一很短的时段内几乎同时到期，此时就可能会引发缓存雪崩问题。
    //    其解决方案是，提前规划好系统中所有缓存的到期时间。
    // 3） 热点缓存：当某一个缓存的有效期到达时其可能会引发热点缓存问题。
    //    其解决方案是，双重检测锁机制

    // 这里要使用双重检测锁机制解决当前代码中可能会存在的热点缓存问题
    // 不存在线程安全问题
    @Override
    public Integer findNumber() {
        // 获取Redis操作对象
        BoundValueOperations<Object, Object> ops = redisTemplate.boundValueOps("count");
        // 从Redis中获取指定key的value
        Object count = ops.get();
        // 双重检测
        if(count==null){
            synchronized (this){
                count=ops.get();
                if(count==null){
                    count=studentDao.count();
                    // 将查询结果存放到Redis，并指定过期时限
                    ops.set(count, 10, TimeUnit.SECONDS);
                }
            }
        }
        return (Integer) count;
    }
}
