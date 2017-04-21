Stage Environment Setup
=======================
The purpose of this document is to cover all the steps required to bring up Stage Environment infrastructure on keas aws us-east-1 region. The VPC and Security Groups[Firewall] are shared for both Dev and Stage Environments. Frontend, Biorecs, and Nest DBs are hosted in the same RDS for both Dev and Stage Environments.

## Contents
* [Pre-Requisites](#pre-requisites)
* [Redis](#redis)
* [RabbitMQ](#rabbitmq)
* [Memcached](#memcached)
* [Resque](#resque)
* [Frontend](#frontend)
* [Biorecs](#biorecs)
* [Nest](#nest)
* [Haproxy](#haproxy)
* [Connection Details](#connection-details)

## Pre-Requisites
1. Access to [Clouformation Repo](https://github.com/welltok/CloudFormation/tree/keas-aws-migration) and [Ansible Repo](https://github.com/welltok/ansible-playbooks/tree/keas-aws-migration)
2. Should have aws cli installed and configured to access keas/welltok aws account
3. Create keas aws cli profile. Your ~/.aws/credentials should look like:
```bash
[keas]
aws_access_key_id = <your access key>
aws_secret_access_key = <your secret key>
```
4. Create a file ~/.ssh/vault.password with the ansible-vault password
5. Place bastion ssh key file under the path ~/.ssh/stagedev-welltokkeas-bastion.key
6. Place the ubuntu user ssh key file under the path ~/.ssh/ec2deploykeasstagedev.key
7. Install Ansible 2.2.0+
8. Edit /etc/ansible/ansible.cfg to add this line
```bash
sudo_flags=-H -i
```
9. Setup ansible on your host based on : [Ansible and Dynamic Amazon EC2 Inventory Management](https://aws.amazon.com/blogs/apn/getting-started-with-ansible-and-dynamic-amazon-ec2-inventory-management/)
10. In /etc/ansible/ec2.ini make sure that the below two are defined so that the dynamic host inventory works fine:
```bash
destination_variable = public_dns_name
vpc_destination_variable = private_ip_address
```
11. Once the bastion is configured with the right users we are using SSHProxyCommand to run all ansible-playbooks to configure all the private servers. The traffic is routed through bastion over SSH.
To achieve this, update your local ~/.ssh/config based on the example shown below:
```bash
Host 10.57.*.*
  User ubuntu
  IdentityFile ~/.ssh/ec2deploykeasstagedev.key
  StrictHostKeyChecking no
  ForwardAgent yes
  ProxyCommand ssh -W %h:%p mcheriyath@54.209.17.79 -i ~/.ssh/mcheriyath-aws-keas
```

## Redis
#### Launch Redis Cluster [Cloudformation Repo]
```bash
cd keas-infrastructure/ec2/stage/redis
sh run.sh
sh create.sh
```
Approx runtime: less than 5 mins

#### Configure Redis Sentinel Cluster [Ansible Repo]
1. cd keas-infrastructure
2. Update /etc/ansible/hosts as per the [HowTo.Doc](https://github.com/welltok/ansible-playbooks/blob/keas-aws-migration/keas-infrastructure/roles/DavidWittman.redis/HOWTO.md). Do **NOT** execute the last command specified in this documentation.
3. Configure Sentinel with
```bash
time ansible-playbook -i /etc/ansible/hosts role-redis.yml -e "remoteuser=ubuntu" --private-key=~/.ssh/ec2deploykeasstagedev.key
```
4. Comment the Sentinel portion in role-redis.yml
5. Re-run the same command mentioned above in step 3
6. To check that the redis servers were properly configured:
  - login to the redis master
  - run this command: ```redis-cli -h localhost```
  - type in "info" and check to see if the amount of slaves connected is the correct amount. If correct, type "exit".
  - cat the /etc/redis/sentinel_****.conf and make sure the IP's are correct, as well as the number of slaves.
Approx runtime: less than 5 mins

#### Restore redis db from Rackspace 
[How to backup and import redis db](http://zdk.blinkenshell.org/redis-backup-and-restore/)
1. Current Redis Rackspace Details
```bash
redis8.keas.com,redis9.keas.com,miscdev2.keas.com ( Have Sentinel setup)
Used by:
keas-biorecs-stage1,
keas-frontend-stage1,
keas-nest-stage1
```
2. Login to miscdev2.keas.com and take the backup
```bash
[mcheriyath@695927-MiscDev2 ~]$ redis-cli -h 192.168.184.140 --rdb redis-stage-rackspacebackup.rdb
SYNC sent to master, writing 6418307 bytes to 'redis-stage-rackspacebackup.rdb'
Transfer finished with success.
```
3. Copy the file onto redis1-stage.keas.com(Master) /var/lib/redis/6379 <br>
4. Stop redis service running on slaves first and then on master
```bash
sudo service redis_6379 stop
```
5. On Redis Master replace /var/lib/redis/6379/dump.rds with /var/lib/redis/6379/redis-stage-rackspacebackup.rdb <br>
6. Start Redis master first and then start the redis slaves
```bash
sudo service redis_6379 start
```


## RabbitMQ
#### Launch Rabbitmq clusters for uat dev environment [Cloudformation Repo]
```bash
cd keas-infrastructure/ec2/stage/rabbitmq
sh run.sh
sh create.sh
```
Approx time: 4 mins

#### Configure Rabbitmq Clusters [Ansible Repo]
```bash
time ansible-playbook -i /etc/ansible/ec2.py role-rabbitmq-app.yml -e "targethost=tag_aws_cloudformation_stack_name_keas_rabbitmq_stage" -e "remoteuser=ubuntu" -e "rabbitmq_app_environment=stage" -e "rabbitmq_master=rabbitmq1-stage" --private-key=~/.ssh/ec2deploykeasstagedev.key --vault-password-file=/home/vagrant/.ssh/vault.password
```
Approx time: 4 mins

To verify that the rabbitmq cluster has been configured correctly, please log into any of the servers and type in: ```sudo rabbitmqctl cluster_status```. If the amount of nodes, is correct, the cluster has been configured correctly.  
Next, make sure the web console is up and running by visiting https://rabbitmq1-stage.keas.com. If you can log in to the console, nginx has been configured correctly.

## Memcached
#### Launch Memcached clusters for stage environments [Cloudformation Repo]
```bash
cd keas-infrastructure/ec2/stage/memcached
sh run.sh
sh create.sh
```
Approx runtime: less than 3 mins

#### Configure Memcached Clusters [Ansible Repo]
```bash
cd keas-infrastructure
time ansible-playbook -i /etc/ansible/ec2.py role-memcached.yml -e "remoteuser=ubuntu" -e "targethost=tag_aws_cloudformation_stack_name_keas_memcached_stage" --private-key=/home/vagrant/.ssh/ec2deploykeasstagedev.key
```
Approx runtime: less than 5 mins

To verify that the memcached is running on the server, please execute the following command:
```nc -z -v <server_ip> 11211```

If the command is successful to connect, the service is up and running.

## Resque
#### Launch Resque(BG Servers)
```bash
cd keas-infrastructure/ec2/stage/bg
sh run.sh
sh create.sh
```
**Note**: Configuration of the resque servers are defined in their respective application configuration given below.

## Frontend
#### Launch Frontend Servers(Web and Index)
```bash
cd keas-infrastructure/ec2/stage/frontend
sh run.sh
sh create.sh
cd keas-infrastructure/ec2/stage/index
sh run.sh
sh create.sh
```

#### Configure Frontend Servers
**Krubyapp Web**
```bash
time ansible-playbook -i /etc/ansible/ec2.py role-krubyapp.yml -e "remoteuser=ubuntu" -e "targethost=tag_aws_cloudformation_stack_name_keas_frontend_stage" -e "rubyversion=1.9.3-p551" -e "application_name=frontend" -e "application_env=staging" -e "bundler_version=1.11.2"  --private-key=/home/vagrant/.ssh/ec2deploykeasstagedev.key --vault-password-file=/home/vagrant/.ssh/vault.password --tags web
```
Approx runtime: 6 mins  

**Krubyapp Resque**
```bash
time ansible-playbook -i /etc/ansible/ec2.py role-krubyapp.yml -e "remoteuser=ubuntu" -e "targethost=tag_Name_frontendstagebg1" -e "rubyversion=1.9.3-p551" -e "application_name=frontend" -e "application_env=staging" -e "bundler_version=1.11.2"  --private-key=/home/vagrant/.ssh/ec2deploykeasstagedev.key --vault-password-file=/home/vagrant/.ssh/vault.password --tags resque
```
Approx runtime: 6 mins

**Krubyapp Sphinx**
```bash
time ansible-playbook -i /etc/ansible/ec2.py role-krubyapp.yml -e "remoteuser=ubuntu" -e "targethost=tag_Name_frontendstageindex1" -e "rubyversion=1.9.3-p551" -e "application_name=frontend" -e "application_env=staging" -e "bundler_version=1.11.2" --private-key=/home/vagrant/.ssh/ec2deploykeasstagedev.key --vault-password-file=/home/vagrant/.ssh/vault.password --tags sphinx
```
Approx runtime: 6 mins

**Krubyapp Admin**
```bash
time ansible-playbook -i /etc/ansible/ec2.py role-krubyapp.yml -e "remoteuser=ubuntu" -e "targethost=tag_Name_frontendstageadmin1" -e "rubyversion=1.9.3-p551" -e "application_name=frontend" -e "application_env=staging" -e "bundler_version=1.11.2"  --private-key=/home/vagrant/.ssh/ec2deploykeasstagedev.key --vault-password-file=/home/vagrant/.ssh/vault.password --tags web,admin
```
Approx runtime: 6 mins

**Frontend Web**
```bash
time ansible-playbook -i /etc/ansible/ec2.py role-keas-frontend-app.yml -e "remoteuser=ubuntu" -e "targethost=tag_aws_cloudformation_stack_name_keas_frontend_stage" -e "application_env=staging" --private-key=/home/vagrant/.ssh/ec2deploykeasstagedev.key --vault-password-file=/home/vagrant/.ssh/vault.password -e "worker_count=6" --tags web
```
Approx runtime: 2 mins

**Frontend Resque(BG)**
```bash
time ansible-playbook -i /etc/ansible/ec2.py role-keas-frontend-app.yml -e "remoteuser=ubuntu" -e "targethost=tag_Name_frontendstagebg1" -e "application_env=staging" --private-key=/home/vagrant/.ssh/ec2deploykeasstagedev.key --vault-password-file=/home/vagrant/.ssh/vault.password -e "worker_count=6" --tags resque
```
Approx runtime: 2 mins

**Frontend Sphinx(Index)**
```bash
time ansible-playbook -i /etc/ansible/ec2.py role-keas-frontend-app.yml -e "remoteuser=ubuntu" -e "targethost=tag_Name_frontendstageindex1" -e "application_env=staging" --private-key=/home/vagrant/.ssh/ec2deploykeasstagedev.key --vault-password-file=/home/vagrant/.ssh/vault.password -e "worker_count=6" --tags sphinx
```
Approx runtime: 2 mins

**Frontend Admin**
```bash
time ansible-playbook -i /etc/ansible/ec2.py role-keas-frontend-app.yml -e "remoteuser=ubuntu" -e "targethost=tag_Name_frontendstageadmin1" -e "application_env=staging" --private-key=/home/vagrant/.ssh/ec2deploykeasstagedev.key --vault-password-file=/home/vagrant/.ssh/vault.password -e "worker_count=6" --tags web,admin
```
Approx runtime: 3 mins

## Biorecs
#### Launch Biorecs Server
```bash
cd keas-infrastructure/ec2/stage/biorecs
sh run.sh
sh create.sh
```
Approx runtime: 3 mins
*Note: The resque servers required for biorecs are already launched while launching the frontend resque servers*  

#### Configure Biorecs Server
**Biorecs web**
```bash
time ansible-playbook -i /etc/ansible/ec2.py role-keas-biorecs-app.yml -e "targethost=tag_Name_biorecsstageweb1" -e "remoteuser=ubuntu" -e "application_env=staging" --private-key=/home/vagrant/.ssh/ec2deploykeasstagedev.key --vault-password-file=/home/vagrant/.ssh/vault.password --tags web
```
Approx runtime: 8 mins

**Biorecs resque**
```bash
time ansible-playbook -i /etc/ansible/ec2.py role-keas-biorecs-app.yml -e "targethost=tag_Name_biorecsstagebg1" -e "remoteuser=ubuntu" -e "application_env=staging" --private-key=/home/vagrant/.ssh/ec2deploykeasstagedev.key --vault-password-file=/home/vagrant/.ssh/vault.password --tags resque
```
Approx runtime: 13 mins

## Nest
#### Launch Nest Server
```bash
cd keas-infrastructure/ec2/uat/nest
sh run.sh
sh create.sh
```
Approx runtime: 3 mins
*Note: The resque servers required for nest are already launched while launching the frontend resque servers*  

#### Configure Nest Server
**Nest web**
```bash
time ansible-playbook -i /etc/ansible/ec2.py role-keas-nest-app.yml -e "targethost=tag_aws_cloudformation_stack_name_keas_nest_stage" -e "remoteuser=ubuntu" -e "application_env=staging" -e "listeners_count=4" -e "worker_count=3" --private-key=/home/vagrant/.ssh/ec2deploykeasstagedev.key --vault-password-file=/home/vagrant/.ssh/vault.password --tags web
```
Approx runtime: 14 mins  

**Nest Resque**
```bash
time ansible-playbook -i /etc/ansible/ec2.py role-keas-nest-app.yml -e "targethost=tag_Name_neststagebg1,tag_Name_neststagebg2" -e "remoteuser=ubuntu" -e "application_env=staging" -e "listeners_count=4" -e "worker_count=3" --private-key=/home/vagrant/.ssh/ec2deploykeasstagedev.key --vault-password-file=/home/vagrant/.ssh/vault.password --tags resque
```
Approx runtime: 20 mins  

## Haproxy
#### Launch Haproxy [Cloudformation Repo]
```bash
cd ec2/stage/haproxy
sh run.sh
sh create.sh
```
**Note: The above commands will create haproxy servers for frontend, biorecs, nest and frontend-admin apps**

#### Configure Haproxy [Ansible Repo]
**Frontend haproxy**  
Update the IPs on file [roles/haproxy/vars/frontend-staging.yml](https://github.com/welltok/ansible-playbooks/blob/keas-aws-migration/keas-infrastructure/roles/haproxy/vars/frontend-staging.yml) with the new private IPs of frontend1-stage.keas.com and frontend2-stage.keas.com servers. Then run the below command.
```bash
time ansible-playbook -i /etc/ansible/ec2.py role-haproxy.yml  -e "targethost=tag_Name_haproxyfrontend1" -e "remoteuser=ubuntu" -e "application_name=frontend" -e "environment=staging" --private-key=/home/vagrant/.ssh/ec2deploykeasstagedev.key --vault-password-file=/home/vagrant/.ssh/vault.password
```

**Biorecs haproxy**  
Update the IP section on file [roles/haproxy/vars/biorecs-staging.yml](https://github.com/welltok/ansible-playbooks/blob/keas-aws-migration/keas-infrastructure/roles/haproxy/vars/biorecs-staging.yml#L5) with the new private IP of biorecs1-stage.keas.com server. Then run the below command.
```bash
time ansible-playbook -i /etc/ansible/ec2.py role-haproxy.yml  -e "targethost=tag_Name_haproxybiorecs1" -e "remoteuser=ubuntu" -e "application_name=biorecs" -e "environment=staging" --private-key=/home/vagrant/.ssh/ec2deploykeasstagedev.key --vault-password-file=/home/vagrant/.ssh/vault.password
```

**Nest haproxy**  
Update the IP section on file [roles/haproxy/vars/nest-staging.yml](https://github.com/welltok/ansible-playbooks/blob/keas-aws-migration/keas-infrastructure/roles/haproxy/vars/nest-staging.yml) with the new private IPs of nest1-stage.keas.com and nest2-stage.keas.com servers. Then run the below command.

```bash
time ansible-playbook -i /etc/ansible/ec2.py role-haproxy.yml  -e "targethost=tag_Name_haproxynest1" -e "remoteuser=ubuntu" -e "application_name=nest" -e "environment=staging" --private-key=/home/vagrant/.ssh/ec2deploykeasstagedev.key --vault-password-file=/home/vagrant/.ssh/vault.password
```

**Frontend-Admin haproxy**  
Update the IP section on file [roles/haproxy/vars/admin-staging.yml](https://github.com/welltok/ansible-playbooks/blob/keas-aws-migration/keas-infrastructure/roles/haproxy/vars/admin-staging.yml#L5) with the new private IP of admin-stage.keas.com server. Then run the below command.

```bash
time ansible-playbook -i /etc/ansible/ec2.py role-haproxy.yml  -e "targethost=tag_Name_haproxyadmin1" -e "remoteuser=ubuntu" -e "application_name=admin" -e "environment=staging" --private-key=/home/vagrant/.ssh/ec2deploykeasstagedev.key --vault-password-file=/home/vagrant/.ssh/vault.password
```

The purpose of these haproxy servers are to route traffic from internet to private nginx web servers with SSL. <br>
Traffic Flows like: <br>
(Public Browser)-----> https://play-stage.keas.com(Public Subnet) ----> https://frontendweb1-stage.keas.com(Private Subnet)

**Elastic IP**
- From the AWS web console we need to create four new Elastic IPs <br>
- ** Associated with play-stage.keas.com <br>
- ** Associated with biorecs-stage.keas.com <br>
- ** Associated with nest-stage.keas.com <br>
- ** Associated with admin-stage.keas.com <br>

The Public DNS server is currently maintained by George Feil. Any entries that needs to be added to the public should be updated to George so that he can add it to the DNS server. <br>

## Connection Details

MySQL Host:  
stagedev-mysql.ct1vjcyxovqq.us-east-1.rds.amazonaws.com

PostgreSQL host:  
ratatoskr-dev-postgresql.ct1vjcyxovqq.us-east-1.rds.amazonaws.com

Sample commands to connect from CLI:
```bash
mysql -h stagedev-mysql.ct1vjcyxovqq.us-east-1.rds.amazonaws.com -u frontend_dev -p --ssl-ca=/etc/rds/ssl/rds-combined-ca-bundle.pem frontend_dev
mysql -h stagedev-mysql.ct1vjcyxovqq.us-east-1.rds.amazonaws.com -u biorecs_dev -p --ssl-ca=/etc/rds/ssl/rds-combined-ca-bundle.pem biorecs_dev
psql -h ratatoskr-dev-postgresql.ct1vjcyxovqq.us-east-1.rds.amazonaws.com -U ratatoskr -W ratatoskr_dev
```
SSL CA Cert is available for download from [AWS rds-combined-ca-bundle.pem](https://s3.amazonaws.com/rds-downloads/rds-combined-ca-bundle.pem)

Since Private DNS resolution is not working over VPN, make sure to **ADD ENTRIES** in your /etc/hosts [Mac] or C:\Windows\System32\Drivers\etc\hosts [Windows] from [hosts file](https://github.com/welltok/ansible-playbooks/blob/keas-aws-migration/keas-infrastructure/hosts)

RabbitMQ Web Console: https://rabbitmq1-stage.keas.com
