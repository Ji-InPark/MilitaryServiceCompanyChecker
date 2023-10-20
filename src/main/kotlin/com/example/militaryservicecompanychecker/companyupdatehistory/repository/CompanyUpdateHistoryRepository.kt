package com.example.militaryservicecompanychecker.companyupdatehistory.repository

import com.example.militaryservicecompanychecker.companyupdatehistory.entity.CompanyUpdateHistory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface CompanyUpdateHistoryRepository : JpaRepository<CompanyUpdateHistory, Long>