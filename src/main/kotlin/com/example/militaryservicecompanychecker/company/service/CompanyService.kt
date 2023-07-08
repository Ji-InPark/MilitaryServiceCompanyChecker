package com.example.militaryservicecompanychecker.company.service

import com.example.militaryservicecompanychecker.company.entity.Company
import com.example.militaryservicecompanychecker.company.enums.GovernmentLocation
import com.example.militaryservicecompanychecker.company.enums.Sector
import com.example.militaryservicecompanychecker.company.enums.ServiceType
import com.example.militaryservicecompanychecker.company.repository.CompanyRepository
import com.example.militaryservicecompanychecker.company.util.EnumUtil.toGovernmentLocation
import com.example.militaryservicecompanychecker.company.util.EnumUtil.toSector
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.springframework.boot.json.GsonJsonParser
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import javax.transaction.Transactional

@Service
class CompanyService(
    private val companyRepository: CompanyRepository,
    private val okHttpClient: OkHttpClient
) {
    fun searchCompanyByRegex(regex: String): List<Company> {
        return companyRepository.findTop5ByCompanyNameRegex(regex)
    }

    fun searchCompany(
        searchName: String,
        governmentLocation: GovernmentLocation?,
        sector: Sector?
    ): List<Company> {
        return companyRepository.findAllByGovernmentLocationOrCompanySectorAndCompanyName(
            searchName,
            governmentLocation?.toString(),
            sector?.toString()
        )
    }

    fun getGovernmentLocations(): Array<GovernmentLocation> {
        return GovernmentLocation.values()
    }

    fun getSectors(): Array<Sector> {
        return Sector.values()
    }

    fun getKreditJobKeyAndUpdateToCompany(id: Long): String {
        val company = companyRepository.findById(id).orElseThrow()

        if (company.kreditJobKey != null) return company.kreditJobKey!!

        val kreditJobKey = getKreditJobKey(company.companyName)
        company.kreditJobKey = kreditJobKey
        companyRepository.saveAndFlush(company)

        return kreditJobKey
    }

    private fun getKreditJobKey(companyKeyword: String): String {
        val body =
            """{"q": "$companyKeyword"}""".toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val response = okHttpClient.newCall(
            Request.Builder()
                .url("https://kreditjob.com/api/search/company")
                .post(body)
                .build()
        ).execute()

        return GsonJsonParser().parseMap(response.body?.string())["PK_NM_HASH"].toString()
    }

    @Transactional
    fun deleteAndCreateCompanyByFile(
        file: MultipartFile,
        serviceType: ServiceType
    ): MutableList<Company> {
        companyRepository.deleteByServiceType(serviceType)

        val companies = createCompanyListByFile(file, serviceType)

        return companyRepository.saveAllAndFlush(companies)
    }

    private fun createCompanyListByFile(
        file: MultipartFile,
        serviceType: ServiceType
    ): MutableList<Company> {
        val csvFile = CSVParser.parse(
            file.inputStream,
            Charsets.UTF_8,
            CSVFormat.DEFAULT
        )
        val records = csvFile.records
        val headerMap = records[0].toList().withIndex().associate { it.value to it.index }

        val companies = mutableListOf<Company>()

        for (i in 1.until(records.size)) {
            val companyName = records[i][headerMap["업체명"]!!]
            val newCompany = Company(
                companyName = companyName,
                governmentLocation = records[i][headerMap["지방청"]!!].toGovernmentLocation(),
                companyLocation = records[i][headerMap["주소"]!!],
                companyPhoneNumber = records[i][headerMap["전화번호"]!!],
                companyFaxNumber = records[i][headerMap["팩스번호"]!!],
                companySector = records[i][headerMap["업종"]!!].toSector(),
                companyScale = records[i][headerMap["기업규모"]!!],
                serviceType = serviceType,
                companyKeyword = companyName.replace("(주)", "").replace("(유)", "")
                    .replace("주식회사", "")
            )
            companies.add(newCompany)
        }

        return companies
    }
}